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

package com.espertech.esper.regression.context;

import com.espertech.esper.client.*;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.util.SupportUpdateListener;
import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;

public class TestContextMTSegmented extends TestCase
{
    private static final Log log = LogFactory.getLog(TestContextMTSegmented.class);

    private EPServiceProvider epService;
    private SupportUpdateListener listener;

    public void setUp()
    {
        Configuration config = SupportConfigFactory.getConfiguration();
        config.addEventType("SupportBean", SupportBean.class);
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epService.initialize();
        listener = new SupportUpdateListener();
    }

    public void testSegmentedContext() throws Exception
    {
        String[] choices = "A,B,C,D".split(",");
        trySend(4, 1000, choices);
    }

    private void trySend(int numThreads, int numEvents, String[] choices) throws Exception
    {
        if (numEvents < choices.length) {
            throw new IllegalArgumentException("Number of events must at least match number of choices");
        }
        
        epService.getEPRuntime().sendEvent(new CurrentTimeEvent(0));
        epService.getEPAdministrator().createEPL("create variable boolean myvar = false");
        epService.getEPAdministrator().createEPL("create context SegmentedByString as partition by string from SupportBean");
        EPStatement stmt = epService.getEPAdministrator().createEPL("context SegmentedByString select string, count(*) - 1 as cnt from SupportBean output snapshot when myvar = true");
        stmt.addListener(listener);

        // preload - since concurrently sending same-category events an event can be dropped
        for (int i = 0; i < choices.length; i++) {
            epService.getEPRuntime().sendEvent(new SupportBean(choices[i], 0));
        }

        EventRunnable[] runnables = new EventRunnable[numThreads];
        for (int i = 0; i < runnables.length; i++) {
            runnables[i] = new EventRunnable(epService, numEvents, choices);
        }

        // start
        Thread[] threads = new Thread[runnables.length];
        for (int i = 0; i < runnables.length; i++) {
            threads[i] = new Thread(runnables[i]);
            threads[i].start();
        }

        // join
        log.info("Waiting for completion");
        for (int i = 0; i < runnables.length; i++) {
            threads[i].join();
        }

        Map<String, Long> totals = new HashMap<String, Long>();
        for (String choice : choices) {
            totals.put(choice, 0L);
        }

        // verify
        int sum = 0;
        for (int i = 0; i < runnables.length; i++) {
            assertNull(runnables[i].getException());
            for (Map.Entry<String, Integer> entry : runnables[i].getTotals().entrySet()) {
                Long current = totals.get(entry.getKey());
                current += entry.getValue();
                sum += entry.getValue();
                totals.put(entry.getKey(), current);
                //System.out.println("Thread " + i + " key " + entry.getKey() + " count " + entry.getValue());
            }
        }

        assertEquals(numThreads * numEvents, sum);

        epService.getEPRuntime().setVariableValue("myvar", true);
        epService.getEPRuntime().sendEvent(new CurrentTimeEvent(10000));
        EventBean[] result = listener.getLastNewData();
        assertEquals(choices.length, result.length);
        for (EventBean item : result) {
            String string = (String) item.get("string");
            Long count = (Long) item.get("cnt");
            //System.out.println("String " + string + " count " + count);
            assertEquals(count, totals.get(string));
        }
    }

    public static class EventRunnable implements Runnable {

        private final EPServiceProvider epService;
        private final int numEvents;
        private final String[] choices;
        private final Map<String, Integer> totals = new HashMap<String, Integer>();

        private RuntimeException exception;

        public EventRunnable(EPServiceProvider epService, int numEvents, String[] choices) {
            this.epService = epService;
            this.numEvents = numEvents;
            this.choices = choices;
        }

        public void run() {
            log.info("Started event send");

            try {
                for (int i = 0; i < numEvents; i++) {
                    String chosen = choices[i % choices.length];
                    epService.getEPRuntime().sendEvent(new SupportBean(chosen, 1));

                    Integer current = totals.get(chosen);
                    if (current == null) {
                        current = 0;
                    }
                    current += 1;
                    totals.put(chosen, current);
                }
            }
            catch (RuntimeException ex) {
                log.error("Exception encountered: " + ex.getMessage(), ex);
                exception = ex;
            }

            log.info("Completed event send");
        }

        public RuntimeException getException() {
            return exception;
        }

        public Map<String, Integer> getTotals() {
            return totals;
        }
    }
}
