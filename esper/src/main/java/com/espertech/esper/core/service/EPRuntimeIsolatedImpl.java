/*
 * *************************************************************************************
 *  Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 *  http://esper.codehaus.org                                                          *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.core.service;

import com.espertech.esper.client.EPException;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventSender;
import com.espertech.esper.client.EventTypeException;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.espertech.esper.client.time.CurrentTimeSpanEvent;
import com.espertech.esper.client.time.TimerControlEvent;
import com.espertech.esper.client.time.TimerEvent;
import com.espertech.esper.collection.ArrayBackedCollection;
import com.espertech.esper.collection.DualWorkQueue;
import com.espertech.esper.collection.ThreadWorkQueue;
import com.espertech.esper.core.context.util.EPStatementAgentInstanceHandle;
import com.espertech.esper.core.context.util.EPStatementAgentInstanceHandleComparator;
import com.espertech.esper.epl.expression.ExprEvaluatorContext;
import com.espertech.esper.epl.expression.ExprEvaluatorContextTimeOnly;
import com.espertech.esper.filter.FilterHandle;
import com.espertech.esper.filter.FilterHandleCallback;
import com.espertech.esper.schedule.ScheduleHandle;
import com.espertech.esper.schedule.ScheduleHandleCallback;
import com.espertech.esper.util.ExecutionPathDebugLog;
import com.espertech.esper.util.ThreadLogUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.util.*;

/**
 * Implementation for isolated runtime.
 */
public class EPRuntimeIsolatedImpl implements EPRuntimeIsolatedSPI, InternalEventRouteDest, EPRuntimeEventSender
{
    private EPServicesContext unisolatedServices;
    private EPIsolationUnitServices services;
    private boolean isSubselectPreeval;
    private boolean isPrioritized;
    private boolean isLatchStatementInsertStream;
    private ExprEvaluatorContext isolatedTimeEvalContext;
    private ThreadWorkQueue threadWorkQueue;

    private ThreadLocal<Map<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>>> matchesPerStmtThreadLocal;
    private ThreadLocal<Map<EPStatementAgentInstanceHandle, Object>> schedulePerStmtThreadLocal;
    private ThreadLocal<ArrayBackedCollection<FilterHandle>> matchesArrayThreadLocal;
    private ThreadLocal<ArrayBackedCollection<ScheduleHandle>> scheduleArrayThreadLocal;

    /**
     * Ctor.
     * @param svc isolated services
     * @param unisolatedSvc engine services
     */
    public EPRuntimeIsolatedImpl(EPIsolationUnitServices svc, EPServicesContext unisolatedSvc)
    {
        this.services = svc;
        this.unisolatedServices = unisolatedSvc;
        this.threadWorkQueue = new ThreadWorkQueue();
        isSubselectPreeval = unisolatedSvc.getEngineSettingsService().getEngineSettings().getExpression().isSelfSubselectPreeval();
        isPrioritized = unisolatedSvc.getEngineSettingsService().getEngineSettings().getExecution().isPrioritized();
        isLatchStatementInsertStream = unisolatedSvc.getEngineSettingsService().getEngineSettings().getThreading().isInsertIntoDispatchPreserveOrder();
        isolatedTimeEvalContext = new ExprEvaluatorContextTimeOnly(services.getSchedulingService());

        initThreadLocals();
    }

    public void sendEvent(Object theEvent) throws EPException
    {
        if (theEvent == null)
        {
            log.fatal(".sendEvent Null object supplied");
            return;
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()))
        {
            if ((!(theEvent instanceof CurrentTimeEvent)) || (ExecutionPathDebugLog.isTimerDebugEnabled))
            {
                log.debug(".sendEvent Processing event " + theEvent);
            }
        }

        // Process event
        processEvent(theEvent);
    }

    public void sendEvent(org.w3c.dom.Node document) throws EPException
    {
        if (document == null)
        {
            log.fatal(".sendEvent Null object supplied");
            return;
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()))
        {
            log.debug(".sendEvent Processing DOM node event " + document);
        }

        // Get it wrapped up, process event
        EventBean eventBean = unisolatedServices.getEventAdapterService().adapterForDOM(document);
        processEvent(eventBean);
    }

    /**
     * Route a XML docment event
     * @param document to route
     * @throws EPException if routing failed
     */
    public void route(org.w3c.dom.Node document) throws EPException
    {
        if (document == null)
        {
            log.fatal(".sendEvent Null object supplied");
            return;
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()))
        {
            log.debug(".sendEvent Processing DOM node event " + document);
        }

        // Get it wrapped up, process event
        EventBean eventBean = unisolatedServices.getEventAdapterService().adapterForDOM(document);
        threadWorkQueue.addBack(eventBean);
    }

    public void sendEvent(Map map, String eventTypeName) throws EPException
    {
        if (map == null)
        {
            throw new IllegalArgumentException("Invalid null event object");
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()))
        {
            log.debug(".sendMap Processing event " + map);
        }

        // Process event
        EventBean eventBean = unisolatedServices.getEventAdapterService().adapterForMap(map, eventTypeName);
        processWrappedEvent(eventBean);
    }

    public void sendEvent(Object[] objectarray, String objectArrayEventTypeName) {
        if (objectarray == null)
        {
            throw new IllegalArgumentException("Invalid null event object");
        }

        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()))
        {
            log.debug(".sendEvent Processing event " + Arrays.toString(objectarray));
        }

        // Process event
        EventBean eventBean = unisolatedServices.getEventAdapterService().adapterForObjectArray(objectarray, objectArrayEventTypeName);
        processWrappedEvent(eventBean);
    }

    /**
     * Process an unwrapped event.
     * @param theEvent to process.
     */
    public void processEvent(Object theEvent)
    {
        if (theEvent instanceof TimerEvent)
        {
            processTimeEvent((TimerEvent) theEvent);
            return;
        }

        EventBean eventBean;

        if (theEvent instanceof EventBean)
        {
            eventBean = (EventBean) theEvent;
        }
        else
        {
            eventBean = unisolatedServices.getEventAdapterService().adapterForBean(theEvent);
        }

        processWrappedEvent(eventBean);
    }

    /**
     * Process a wrapped event.
     * @param eventBean to process
     */
    public void processWrappedEvent(EventBean eventBean)
    {
        // Acquire main processing lock which locks out statement management
        unisolatedServices.getEventProcessingRWLock().acquireReadLock();
        try
        {
            processMatches(eventBean);
        }
        catch (RuntimeException ex)
        {
            matchesArrayThreadLocal.get().clear();
            throw new EPException(ex);
        }
        finally
        {
            unisolatedServices.getEventProcessingRWLock().releaseReadLock();
        }

        // Dispatch results to listeners
        // Done outside of the read-lock to prevent lockups when listeners create statements
        dispatch();

        // Work off the event queue if any events accumulated in there via a route() or insert-into
        processThreadWorkQueue();
    }

    private void processTimeEvent(TimerEvent theEvent)
    {
        if (theEvent instanceof TimerControlEvent) {
            TimerControlEvent tce = (TimerControlEvent) theEvent;
            if (tce.getClockType() == TimerControlEvent.ClockType.CLOCK_INTERNAL) {
                log.warn("Timer control events are not processed by the isolated runtime as the setting is always external timer.");                
            }
            return;
        }

        // Evaluation of all time events is protected from statement management
        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()) && (ExecutionPathDebugLog.isTimerDebugEnabled))
        {
            log.debug(".processTimeEvent Setting time and evaluating schedules");
        }

        if (theEvent instanceof CurrentTimeEvent) {
            CurrentTimeEvent current = (CurrentTimeEvent) theEvent;
            long currentTime = current.getTimeInMillis();

            if (currentTime == services.getSchedulingService().getTime())
            {
                if (log.isWarnEnabled())
                {
                    log.warn("Duplicate time event received for currentTime " + currentTime);
                }
            }
            services.getSchedulingService().setTime(currentTime);

            processSchedule();

            // Let listeners know of results
            dispatch();

            // Work off the event queue if any events accumulated in there via a route()
            processThreadWorkQueue();

            return;
        }

        // handle time span
        CurrentTimeSpanEvent span = (CurrentTimeSpanEvent) theEvent;
        long targetTime = span.getTargetTimeInMillis();
        long currentTime = services.getSchedulingService().getTime();
        Long optionalResolution = span.getOptionalResolution();

        if (targetTime < currentTime)
        {
            if (log.isWarnEnabled())
            {
                log.warn("Past or current time event received for currentTime " + targetTime);
            }
        }

        // Evaluation of all time events is protected from statement management
        if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()) && (ExecutionPathDebugLog.isTimerDebugEnabled))
        {
            log.debug(".processTimeEvent Setting time span and evaluating schedules for time " + targetTime + " optional resolution " + span.getOptionalResolution());
        }

        while(currentTime < targetTime) {

            if ((optionalResolution != null) && (optionalResolution > 0)) {
                currentTime += optionalResolution;
            }
            else {
                Long nearest = services.getSchedulingService().getNearestTimeHandle();
                if (nearest == null) {
                    currentTime = targetTime;
                }
                else {
                    currentTime = nearest;
                }
            }
            if (currentTime > targetTime) {
                currentTime = targetTime;
            }

            // Evaluation of all time events is protected from statement management
            if ((ExecutionPathDebugLog.isDebugEnabled) && (log.isDebugEnabled()) && (ExecutionPathDebugLog.isTimerDebugEnabled))
            {
                log.debug(".processTimeEvent Setting time and evaluating schedules for time " + currentTime);
            }

            services.getSchedulingService().setTime(currentTime);

            processSchedule();

            // Let listeners know of results
            dispatch();

            // Work off the event queue if any events accumulated in there via a route()
            processThreadWorkQueue();
        }
    }

    private void processSchedule()
    {
        ArrayBackedCollection<ScheduleHandle> handles = scheduleArrayThreadLocal.get();

        // Evaluation of schedules is protected by an optional scheduling service lock and then the engine lock
        // We want to stay in this order for allowing the engine lock as a second-order lock to the
        // services own lock, if it has one.
        unisolatedServices.getEventProcessingRWLock().acquireReadLock();
        try
        {
            services.getSchedulingService().evaluate(handles);
        }
        catch (RuntimeException ex)
        {
            throw ex;
        }
        finally
        {
            unisolatedServices.getEventProcessingRWLock().releaseReadLock();
        }

        unisolatedServices.getEventProcessingRWLock().acquireReadLock();
        try
        {
            processScheduleHandles(handles);
        }
        catch (RuntimeException ex)
        {
            handles.clear();
            throw ex;
        }
        finally
        {
            unisolatedServices.getEventProcessingRWLock().releaseReadLock();
        }
    }

    private void processScheduleHandles(ArrayBackedCollection<ScheduleHandle> handles)
    {
        if (ThreadLogUtil.ENABLED_TRACE)
        {
            ThreadLogUtil.trace("Found schedules for", handles.size());
        }

        if (handles.size() == 0)
        {
            return;
        }

        // handle 1 result separatly for performance reasons
        if (handles.size() == 1)
        {
            Object[] handleArray = handles.getArray();
            EPStatementHandleCallback handle = (EPStatementHandleCallback) handleArray[0];

            EPRuntimeImpl.processStatementScheduleSingle(handle, unisolatedServices, isolatedTimeEvalContext);

            handles.clear();
            return;
        }

        Object[] matchArray = handles.getArray();
        int entryCount = handles.size();

        // sort multiple matches for the event into statements
        Map<EPStatementAgentInstanceHandle, Object> stmtCallbacks = schedulePerStmtThreadLocal.get();
        stmtCallbacks.clear();
        for (int i = 0; i < entryCount; i++)    // need to use the size of the collection
        {
            EPStatementHandleCallback handleCallback = (EPStatementHandleCallback) matchArray[i];
            EPStatementAgentInstanceHandle handle = handleCallback.getAgentInstanceHandle();
            ScheduleHandleCallback callback = handleCallback.getScheduleCallback();

            Object entry = stmtCallbacks.get(handle);

            // This statement has not been encountered before
            if (entry == null)
            {
                stmtCallbacks.put(handle, callback);
                continue;
            }

            // This statement has been encountered once before
            if (entry instanceof ScheduleHandleCallback)
            {
                ScheduleHandleCallback existingCallback = (ScheduleHandleCallback) entry;
                ArrayDeque<ScheduleHandleCallback> entries = new ArrayDeque<ScheduleHandleCallback>();
                entries.add(existingCallback);
                entries.add(callback);
                stmtCallbacks.put(handle, entries);
                continue;
            }

            // This statement has been encountered more then once before
            ArrayDeque<ScheduleHandleCallback> entries = (ArrayDeque<ScheduleHandleCallback>) entry;
            entries.add(callback);
        }
        handles.clear();

        for (Map.Entry<EPStatementAgentInstanceHandle, Object> entry : stmtCallbacks.entrySet())
        {
            EPStatementAgentInstanceHandle handle = entry.getKey();
            Object callbackObject = entry.getValue();

            EPRuntimeImpl.processStatementScheduleMultiple(handle, callbackObject, unisolatedServices, isolatedTimeEvalContext);

            if ((isPrioritized) && (handle.isPreemptive()))
            {
                break;
            }
        }
    }

    /**
     * Works off the thread's work queue.
     */
    public void processThreadWorkQueue()
    {
        DualWorkQueue queues = threadWorkQueue.getThreadQueue();

        if (queues.getFrontQueue().isEmpty()) {
            boolean haveDispatched = unisolatedServices.getNamedWindowService().dispatch(isolatedTimeEvalContext);
            if (haveDispatched)
            {
                // Dispatch results to listeners
                dispatch();
                if (!queues.getFrontQueue().isEmpty()) {
                    processThreadWorkQueueFront(queues);
                }
            }
        }
        else {
            processThreadWorkQueueFront(queues);
        }

        Object item;
        while ( (item = queues.getBackQueue().poll()) != null)
        {
            if (item instanceof InsertIntoLatchSpin)
            {
                processThreadWorkQueueLatchedSpin((InsertIntoLatchSpin) item);
            }
            else if (item instanceof InsertIntoLatchWait)
            {
                processThreadWorkQueueLatchedWait((InsertIntoLatchWait) item);
            }
            else
            {
                processThreadWorkQueueUnlatched(item);
            }

            boolean haveDispatched = unisolatedServices.getNamedWindowService().dispatch(isolatedTimeEvalContext);
            if (haveDispatched)
            {
                dispatch();
            }

            if (!queues.getFrontQueue().isEmpty()) {
                processThreadWorkQueueFront(queues);
            }
        }
    }

    private void processThreadWorkQueueFront(DualWorkQueue queues) {
        Object item;
        while ( (item = queues.getFrontQueue().poll()) != null)
        {
            if (item instanceof InsertIntoLatchSpin)
            {
                processThreadWorkQueueLatchedSpin((InsertIntoLatchSpin) item);
            }
            else if (item instanceof InsertIntoLatchWait)
            {
                processThreadWorkQueueLatchedWait((InsertIntoLatchWait) item);
            }
            else
            {
                processThreadWorkQueueUnlatched(item);
            }

            boolean haveDispatched = unisolatedServices.getNamedWindowService().dispatch(isolatedTimeEvalContext);
            if (haveDispatched)
            {
                dispatch();
            }
        }
    }

    private void processThreadWorkQueueLatchedWait(InsertIntoLatchWait insertIntoLatch)
    {
        // wait for the latch to complete
        EventBean eventBean = insertIntoLatch.await();

        unisolatedServices.getEventProcessingRWLock().acquireReadLock();
        try
        {
            processMatches(eventBean);
        }
        catch (RuntimeException ex)
        {
            matchesArrayThreadLocal.get().clear();
            throw ex;
        }
        finally
        {
            insertIntoLatch.done();
            unisolatedServices.getEventProcessingRWLock().releaseReadLock();
        }

        dispatch();
    }

    private void processThreadWorkQueueLatchedSpin(InsertIntoLatchSpin insertIntoLatch)
    {
        // wait for the latch to complete
        EventBean eventBean = insertIntoLatch.await();

        unisolatedServices.getEventProcessingRWLock().acquireReadLock();
        try
        {
            processMatches(eventBean);
        }
        catch (RuntimeException ex)
        {
            matchesArrayThreadLocal.get().clear();
            throw ex;
        }
        finally
        {
            insertIntoLatch.done();
            unisolatedServices.getEventProcessingRWLock().releaseReadLock();
        }

        dispatch();
    }

    private void processThreadWorkQueueUnlatched(Object item)
    {
        EventBean eventBean;
        if (item instanceof EventBean)
        {
            eventBean = (EventBean) item;
        }
        else
        {
            eventBean = unisolatedServices.getEventAdapterService().adapterForBean(item);
        }

        unisolatedServices.getEventProcessingRWLock().acquireReadLock();
        try
        {
            processMatches(eventBean);
        }
        catch (RuntimeException ex)
        {
            matchesArrayThreadLocal.get().clear();
            throw ex;
        }
        finally
        {
            unisolatedServices.getEventProcessingRWLock().releaseReadLock();
        }

        dispatch();
    }

    private void processMatches(EventBean theEvent)
    {
        // get matching filters
        ArrayBackedCollection<FilterHandle> matches = matchesArrayThreadLocal.get();
        services.getFilterService().evaluate(theEvent, matches);

        if (ThreadLogUtil.ENABLED_TRACE)
        {
            ThreadLogUtil.trace("Found matches for underlying ", matches.size(), theEvent.getUnderlying());
        }

        if (matches.size() == 0)
        {
            return;
        }

        Map<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>> stmtCallbacks = matchesPerStmtThreadLocal.get();
        Object[] matchArray = matches.getArray();
        int entryCount = matches.size();

        for (int i = 0; i < entryCount; i++)
        {
            EPStatementHandleCallback handleCallback = (EPStatementHandleCallback) matchArray[i];
            EPStatementAgentInstanceHandle handle = handleCallback.getAgentInstanceHandle();

            // Self-joins require that the internal dispatch happens after all streams are evaluated.
            // Priority or preemptive settings also require special ordering.
            if (handle.isCanSelfJoin() || isPrioritized)
            {
                ArrayDeque<FilterHandleCallback> callbacks = stmtCallbacks.get(handle);
                if (callbacks == null)
                {
                    callbacks = new ArrayDeque<FilterHandleCallback>();
                    stmtCallbacks.put(handle, callbacks);
                }
                callbacks.add(handleCallback.getFilterCallback());
                continue;
            }

            processStatementFilterSingle(handle, handleCallback, theEvent);
        }
        matches.clear();
        if (stmtCallbacks.isEmpty())
        {
            return;
        }

        for (Map.Entry<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>> entry : stmtCallbacks.entrySet())
        {
            EPStatementAgentInstanceHandle handle = entry.getKey();
            ArrayDeque<FilterHandleCallback> callbackList = entry.getValue();

            processStatementFilterMultiple(handle, callbackList, theEvent);

            if ((isPrioritized) && (handle.isPreemptive()))
            {
                break;
            }
        }
        stmtCallbacks.clear();
    }

    /**
     * Processing multiple filter matches for a statement.
     * @param handle statement handle
     * @param callbackList object containing callbacks
     * @param theEvent to process
     */
    public void processStatementFilterMultiple(EPStatementAgentInstanceHandle handle, ArrayDeque<FilterHandleCallback> callbackList, EventBean theEvent)
    {
        handle.getStatementAgentInstanceLock().acquireWriteLock(unisolatedServices.getStatementLockFactory());
        try
        {
            if (handle.isHasVariables())
            {
                unisolatedServices.getVariableService().setLocalVersion();
            }

            if (isSubselectPreeval)
            {
                // sub-selects always go first
                for (FilterHandleCallback callback : callbackList)
                {
                    if (callback.isSubSelect())
                    {
                        callback.matchFound(theEvent, callbackList);
                    }
                }

                for (FilterHandleCallback callback : callbackList)
                {
                    if (!callback.isSubSelect())
                    {
                        callback.matchFound(theEvent, callbackList);
                    }
                }
            }
            else
            {
                // sub-selects always go last
                for (FilterHandleCallback callback : callbackList)
                {
                    if (!callback.isSubSelect())
                    {
                        callback.matchFound(theEvent, callbackList);
                    }
                }

                for (FilterHandleCallback callback : callbackList)
                {
                    if (callback.isSubSelect())
                    {
                        callback.matchFound(theEvent, callbackList);
                    }
                }
            }

            // internal join processing, if applicable
            handle.internalDispatch(isolatedTimeEvalContext);
        }
        catch (RuntimeException ex)
        {
            unisolatedServices.getExceptionHandlingService().handleException(ex, handle);
        }
        finally
        {
            handle.getStatementAgentInstanceLock().releaseWriteLock(unisolatedServices.getStatementLockFactory());
        }
    }

    /**
     * Process a single match.
     * @param handle statement
     * @param handleCallback callback
     * @param theEvent event to indicate
     */
    public void processStatementFilterSingle(EPStatementAgentInstanceHandle handle, EPStatementHandleCallback handleCallback, EventBean theEvent)
    {
        handle.getStatementAgentInstanceLock().acquireWriteLock(unisolatedServices.getStatementLockFactory());
        try
        {
            if (handle.isHasVariables())
            {
                unisolatedServices.getVariableService().setLocalVersion();
            }

            handleCallback.getFilterCallback().matchFound(theEvent, null);

            // internal join processing, if applicable
            handle.internalDispatch(isolatedTimeEvalContext);
        }
        catch (RuntimeException ex)
        {
            unisolatedServices.getExceptionHandlingService().handleException(ex, handle);
        }
        finally
        {
            handleCallback.getAgentInstanceHandle().getStatementAgentInstanceLock().releaseWriteLock(unisolatedServices.getStatementLockFactory());
        }
    }

    /**
     * Dispatch events.
     */
    public void dispatch()
    {
        try
        {
            unisolatedServices.getDispatchService().dispatch();
        }
        catch (RuntimeException ex)
        {
            throw new EPException(ex);
        }
    }

    /**
     * Destroy for destroying an engine instance: sets references to null and clears thread-locals
     */
    public void destroy()
    {
        services = null;

        if (matchesArrayThreadLocal != null) {
            matchesArrayThreadLocal.remove();
        }
        if (matchesPerStmtThreadLocal != null) {
            matchesPerStmtThreadLocal.remove();
        }
        if (scheduleArrayThreadLocal != null) {
            scheduleArrayThreadLocal.remove();
        }
        if (schedulePerStmtThreadLocal != null) {
            schedulePerStmtThreadLocal.remove();
        }

        matchesArrayThreadLocal = null;
        matchesPerStmtThreadLocal = null;
        scheduleArrayThreadLocal = null;
        schedulePerStmtThreadLocal = null;
    }

    public long getCurrentTime() {
        return services.getSchedulingService().getTime();
    }

    // Internal route of events via insert-into, holds a statement lock
    public void route(EventBean theEvent, EPStatementHandle epStatementHandle, boolean addToFront)
    {
        if (isLatchStatementInsertStream)
        {
            if (addToFront) {
                Object latch = epStatementHandle.getInsertIntoFrontLatchFactory().newLatch(theEvent);
                threadWorkQueue.addFront(latch);
            }
            else {
                Object latch = epStatementHandle.getInsertIntoBackLatchFactory().newLatch(theEvent);
                threadWorkQueue.addBack(latch);
            }
        }
        else
        {
            if (addToFront) {
                  threadWorkQueue.addFront(theEvent);
            }
            else {
                threadWorkQueue.addBack(theEvent);
            }
        }
    }

    public void setInternalEventRouter(InternalEventRouter internalEventRouter)
    {
        throw new UnsupportedOperationException("Isolated runtime does not route itself");
    }

    public Long getNextScheduledTime() {
        return services.getSchedulingService().getNearestTimeHandle();
    }

    public Map<String, Long> getStatementNearestSchedules() {
        return EPRuntimeImpl.getStatementNearestSchedulesInternal(services.getSchedulingService(), unisolatedServices.getStatementLifecycleSvc());
    }

    public String getEngineURI() {
        return unisolatedServices.getEngineURI();
    }

    private void initThreadLocals() {
        matchesPerStmtThreadLocal =
                new ThreadLocal<Map<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>>>()
                {
                    protected synchronized Map<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>> initialValue()
                    {
                        if (isPrioritized)
                        {
                            return new TreeMap<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>>(EPStatementAgentInstanceHandleComparator.INSTANCE);
                        }
                        else
                        {
                            return new HashMap<EPStatementAgentInstanceHandle, ArrayDeque<FilterHandleCallback>>();
                        }
                    }
                };

        schedulePerStmtThreadLocal = new ThreadLocal<Map<EPStatementAgentInstanceHandle, Object>>()
        {
            protected synchronized Map<EPStatementAgentInstanceHandle, Object> initialValue()
            {
                if (isPrioritized)
                {
                    return new TreeMap<EPStatementAgentInstanceHandle, Object>(EPStatementAgentInstanceHandleComparator.INSTANCE);
                }
                else
                {
                    return new HashMap<EPStatementAgentInstanceHandle, Object>();
                }
            }
        };

        matchesArrayThreadLocal = new ThreadLocal<ArrayBackedCollection<FilterHandle>>()
        {
            protected synchronized ArrayBackedCollection<FilterHandle> initialValue()
            {
                return new ArrayBackedCollection<FilterHandle>(100);
            }
        };

        scheduleArrayThreadLocal = new ThreadLocal<ArrayBackedCollection<ScheduleHandle>>()
        {
            protected synchronized ArrayBackedCollection<ScheduleHandle> initialValue()
            {
                return new ArrayBackedCollection<ScheduleHandle>(100);
            }
        };
    }

    public EventSender getEventSender(String eventTypeName)
    {
        return unisolatedServices.getEventAdapterService().getStaticTypeEventSender(this, eventTypeName, unisolatedServices.getThreadingService());
    }

    public EventSender getEventSender(URI uri[]) throws EventTypeException
    {
        return unisolatedServices.getEventAdapterService().getDynamicTypeEventSender(this, uri, unisolatedServices.getThreadingService());
    }

    public void routeEventBean(EventBean theEvent)
    {
        threadWorkQueue.addBack(theEvent);
    }

    private static final Log log = LogFactory.getLog(EPRuntimeImpl.class);
}
