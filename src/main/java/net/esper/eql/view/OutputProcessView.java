package net.esper.eql.view;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.esper.collection.MultiKey;
import net.esper.collection.Pair;
import net.esper.eql.expression.OutputLimitSpec;
import net.esper.eql.expression.ResultSetProcessor;
import net.esper.eql.join.JoinSetIndicator;
import net.esper.event.EventBean;
import net.esper.event.EventType;
import net.esper.view.ViewServiceContext;
import net.esper.view.ViewSupport;
import net.esper.view.Viewable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A view that prepares output events, batching incoming 
 * events and invoking the result set processor as necessary.
 * 
 */
public class OutputProcessView extends ViewSupport implements JoinSetIndicator
{
    private final ResultSetProcessor resultSetProcessor;
    private final boolean outputLastOnly;
    private final OutputCondition outputCondition;
    private List<EventBean> newEventsList = new LinkedList<EventBean>();
	private List<EventBean> oldEventsList = new LinkedList<EventBean>();
	private Set<MultiKey<EventBean>> newEventsSet = new LinkedHashSet<MultiKey<EventBean>>();
	private Set<MultiKey<EventBean>> oldEventsSet = new LinkedHashSet<MultiKey<EventBean>>();

	private static final Log log = LogFactory.getLog(OutputProcessView.class);

    /**
     * Ctor.
     * @param resultSetProcessor is processing the result set for publishing it out
     * @param streamCount is the number of streams, indicates whether or not this view participates in a join
     * @param outputLimitSpec is the specification for limiting output (the output condition and the result set processor)
     * @param viewContext is the services the output condition may depend on
     */
    public OutputProcessView(ResultSetProcessor resultSetProcessor, 
    					  int streamCount, 
    					  OutputLimitSpec outputLimitSpec, 
    					  ViewServiceContext viewContext)
    {
    	log.debug("creating view");
 
    	if(streamCount < 1)
    	{
    		throw new IllegalArgumentException("Output process view is part of at least 1 stream");
    	}

    	OutputCallback outputCallback = getCallbackToLocal(streamCount);		
    	this.outputCondition = OutputConditionFactory.createCondition(outputLimitSpec, viewContext, outputCallback);
        this.resultSetProcessor = resultSetProcessor;
        this.outputLastOnly = outputLimitSpec != null ?
        		outputLimitSpec.isDisplayLastOnly() :
        			false;
    }
    
	private OutputCallback getCallbackToLocal(int streamCount)
	{
		// single stream means no join
		// multiple streams means a join
		if(streamCount == 1)
		{
			return new OutputCallback() 
			{
				public void continueOutputProcessing(boolean forceUpdate)
				{
					OutputProcessView.this.continueOutputProcessingView(false);
				}
			};
		}
		else
		{
			return new OutputCallback() 
			{
				public void continueOutputProcessing(boolean forceUpdate)
				{
					OutputProcessView.this.continueOutputProcessingJoin(false);
				}
			};
		}
	}
	
	/**
	 * Called once the output condition has been met.
	 * Invokes the result set processor.
	 * Used for non-join event data.
	 * @param forceUpdate is a flag to indicate that even if there is no data, 
	 * child views should still be updated.
	 * */
	protected void continueOutputProcessingView(boolean forceUpdate)
	{
		log.debug(".continueOutputProcessingView");			
		
		EventBean[] newEvents = newEventsList.toArray(new EventBean[0]);
		EventBean[] oldEvents = oldEventsList.toArray(new EventBean[0]);		
		// convert 0 length arrays to nulls, to prevent
		// the result set processor from working needlessly
		if(newEvents.length == 0)
		{
			newEvents = null;
		}
		if(oldEvents.length == 0)
		{
			oldEvents = null;
		}
		
		if(resultSetProcessor != null)
		{
			Pair<EventBean[], EventBean[]> newOldEvents = resultSetProcessor.processViewResult(newEvents, oldEvents);
			
			if (newOldEvents != null)
			{
				this.updateChildren(newOldEvents.getFirst(), newOldEvents.getSecond());
			}
			else if(forceUpdate)
			{
				this.updateChildren(null, null);
			}
		}
		else 
		{
			if(outputLastOnly)
			{
				EventBean[] lastNew = newEvents == null ? null : new EventBean[] { newEvents[newEvents.length - 1] };
				EventBean[] lastOld = oldEvents == null ? null : new EventBean[] { oldEvents[oldEvents.length - 1] };
				this.updateChildren(lastNew, lastOld);
			}
			else
			{
				this.updateChildren(newEvents, oldEvents);
			}
		}
		
		// reset the local event batches
		newEventsList = new LinkedList<EventBean>();
		oldEventsList = new LinkedList<EventBean>();
	}
	
	/**
	 * Called once the output condition has been met.
	 * Invokes the result set processor.
	 * Used for join event data.
	 * @param forceUpdate is a flag to indicate that even if there is no data, 
	 * child views should still be updated.
	 */
	protected void continueOutputProcessingJoin(boolean forceUpdate)
	{
		log.debug(".continueOutputProcessingJoin");			
		
		Pair<EventBean[], EventBean[]> newOldEvents = resultSetProcessor.processJoinResult(newEventsSet, oldEventsSet);
		
		if (newOldEvents != null)
		{
			this.updateChildren(newOldEvents.getFirst(), newOldEvents.getSecond());
		} 		
		else if(forceUpdate)
		{
			this.updateChildren(null, null);
		}
		// reset the local event batches
		newEventsSet = new HashSet<MultiKey<EventBean>>();
		oldEventsSet = new HashSet<MultiKey<EventBean>>();
	}

    /**
     * The update method is called if the view does not participate in a join.
     * @param newData - new events
     * @param oldData - old events
     */
    public void update(EventBean[] newData, EventBean[] oldData)
    {
        if (log.isDebugEnabled())
        {
            log.debug(".update Received update, " +
                    "  newData.length==" + ((newData == null) ? 0 : newData.length) +
                    "  oldData.length==" + ((oldData == null) ? 0 : oldData.length));
        }
        
        // add the incoming events to the event batches
        int newDataLength = 0;
        int oldDataLength = 0;
        if(newData != null)
        {
        	newDataLength = newData.length;
        	for(EventBean event : newData)
        	{
        		newEventsList.add(event);
        	}
        }
        if(oldData != null)
        {
        	oldDataLength = oldData.length;
        	for(EventBean event : oldData)
        	{
        		oldEventsList.add(event);
        	}
        }
        
        outputCondition.updateOutputCondition(newDataLength, oldDataLength);
    }

    /**
     * This process (update) method is for participation in a join.
     * @param newEvents - new events
     * @param oldEvents - old events
     */
    public void process(Set<MultiKey<EventBean>> newEvents, Set<MultiKey<EventBean>> oldEvents)
    {
        if (log.isDebugEnabled())
        {
            log.debug(".process Received update, " +
                    "  newData.length==" + ((newEvents == null) ? 0 : newEvents.size()) +
                    "  oldData.length==" + ((oldEvents == null) ? 0 : oldEvents.size()));
        }
        
		// add the incoming events to the event batches
		for(MultiKey<EventBean> event : newEvents)
		{
			newEventsSet.add(event);
		}
		for(MultiKey<EventBean> event : oldEvents)
		{
			oldEventsSet.add(event);
		}
		
		outputCondition.updateOutputCondition(newEvents.size(), oldEvents.size());
    }

    public EventType getEventType()
    {
    	if(resultSetProcessor != null)
    	{
    		return resultSetProcessor.getResultEventType();
    	}
    	else 
    	{
    		return parent.getEventType();
    	}
    }

    public Iterator<EventBean> iterator()
    {
    	if(resultSetProcessor != null)
    	{
    		throw new UnsupportedOperationException();
    	}
    	else 
    	{
    		return parent.iterator();
    	}
    }


    public String attachesTo(Viewable parentViewable)
    {
        return null;
    }
}
