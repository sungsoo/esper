package com.espertech.esper.epl.thread;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.collection.UniformPair;
import com.espertech.esper.core.StatementResultServiceImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class OutboundUnitRunnable implements Runnable
{
    private static final Log log = LogFactory.getLog(OutboundUnitRunnable.class);

    private final UniformPair<EventBean[]> events;
    private final StatementResultServiceImpl statementResultService;

    public OutboundUnitRunnable(UniformPair<EventBean[]> events, StatementResultServiceImpl statementResultService)
    {
        this.events = events;
        this.statementResultService = statementResultService;
    }

    public void run()
    {
        try
        {
            statementResultService.processDispatch(events);
        }
        catch (RuntimeException e)
        {
            log.error(e); // TODO
        }
    }
}
