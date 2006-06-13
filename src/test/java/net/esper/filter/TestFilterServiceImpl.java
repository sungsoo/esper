package net.esper.filter;

import net.esper.support.filter.SupportFilterSpecBuilder;
import net.esper.support.filter.SupportFilterCallback;
import net.esper.support.bean.SupportBean;
import net.esper.support.bean.SupportBeanSimple;
import net.esper.event.EventType;
import net.esper.event.EventTypeFactory;
import net.esper.event.EventBean;
import net.esper.event.EventBeanFactory;

import java.util.Vector;

import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TestFilterServiceImpl extends TestCase
{
    private EventType eventTypeOne;
    private EventType eventTypeTwo;
    private FilterServiceImpl filterService;
    private Vector<FilterValueSet> filterSpecs;
    private Vector<SupportFilterCallback> filterCallbacks;
    private Vector<EventBean> events;
    private Vector<int[]> matchesExpected;

    public void setUp()
    {
        filterService = new FilterServiceImpl();

        eventTypeOne = EventTypeFactory.getInstance().createBeanType(SupportBean.class);
        eventTypeTwo = EventTypeFactory.getInstance().createBeanType(SupportBeanSimple.class);

        filterSpecs = new Vector<FilterValueSet>();
        filterSpecs.add(SupportFilterSpecBuilder.build(eventTypeOne, new Object[0]).getValueSet(null));
        filterSpecs.add(SupportFilterSpecBuilder.build(eventTypeOne, new Object[] {
                "intPrimitive", FilterOperator.RANGE_CLOSED, 10, 20,
                "string", FilterOperator.EQUAL, "HELLO",
                "boolPrimitive", FilterOperator.EQUAL, false,
                "doubleBoxed", FilterOperator.GREATER, 100d} ).getValueSet(null));
        filterSpecs.add(SupportFilterSpecBuilder.build(eventTypeTwo, new Object[0]).getValueSet(null));
        filterSpecs.add(SupportFilterSpecBuilder.build(eventTypeTwo, new Object[] {
                "myInt", FilterOperator.RANGE_HALF_CLOSED, 1, 10,
                "myString", FilterOperator.EQUAL, "Hello" }).getValueSet(null));

        // Create callbacks and add
        filterCallbacks = new Vector<SupportFilterCallback>();
        for (int i = 0; i < filterSpecs.size(); i++)
        {
            filterCallbacks.add(new SupportFilterCallback());
            filterService.add(filterSpecs.get(i), filterCallbacks.get(i));
        }

        // Create events
        matchesExpected = new Vector<int[]>();
        events = new Vector<EventBean>();

        events.add(makeTypeOneEvent(15, "HELLO", false, 101));
        matchesExpected.add(new int[] {1, 1, 0, 0});

        events.add(makeTypeTwoEvent("Hello", 100));
        matchesExpected.add(new int[] {0, 0, 1, 0});

        events.add(makeTypeTwoEvent("Hello", 1));       // eventNumber = 2
        matchesExpected.add(new int[] {0, 0, 1, 0});

        events.add(makeTypeTwoEvent("Hello", 2));
        matchesExpected.add(new int[] {0, 0, 1, 1});

        events.add(makeTypeOneEvent(15, "HELLO", true, 100));
        matchesExpected.add(new int[] {1, 0, 0, 0});

        events.add(makeTypeOneEvent(15, "HELLO", false, 99));
        matchesExpected.add(new int[] {1, 0, 0, 0});

        events.add(makeTypeOneEvent(9, "HELLO", false, 100));
        matchesExpected.add(new int[] {1, 0, 0, 0});

        events.add(makeTypeOneEvent(10, "no", false, 100));
        matchesExpected.add(new int[] {1, 0, 0, 0});

        events.add(makeTypeOneEvent(15, "HELLO", false, 999999));      // number 8
        matchesExpected.add(new int[] {1, 1, 0, 0});

        events.add(makeTypeTwoEvent("Hello", 10));
        matchesExpected.add(new int[] {0, 0, 1, 1});

        events.add(makeTypeTwoEvent("Hello", 11));
        matchesExpected.add(new int[] {0, 0, 1, 0});
    }

    public void testEvalEvents()
    {
        for (int i = 0; i < events.size(); i++)
        {
            filterService.evaluate(events.get(i));
            int[] matches = matchesExpected.get(i);

            for (int j = 0; j < matches.length; j++)
            {
                SupportFilterCallback callback = filterCallbacks.get(j);

                if (matches[j] != callback.getAndResetCountInvoked())
                {
                    log.debug(".testEvalEvents Match failed, event=" + events.get(i).getUnderlying());
                    log.debug(".testEvalEvents Match failed, eventNumber=" + i + " index=" + j);
                    assertTrue(false);
                }
            }
        }
    }

    public void testInvalidType()
    {
        try
        {
            FilterValueSet spec = SupportFilterSpecBuilder.build(eventTypeTwo, new Object[] {
                "myString", FilterOperator.GREATER, 2 }).getValueSet(null);
            filterService.add(spec, new SupportFilterCallback());
            assertTrue(false);
        }
        catch (IllegalArgumentException ex)
        {
            // Expected exception
        }
    }

    public void testReusedCallback()
    {
        try
        {
            filterService.add(filterSpecs.get(0), filterCallbacks.get(0));
            assertTrue(false);
        }
        catch (IllegalStateException ex)
        {
            // Expected exception
        }
    }

    public void testCallbackNoFound()
    {
        try
        {
            filterService.remove(filterCallbacks.get(0));
            filterService.remove(filterCallbacks.get(0));
            assertTrue(false);
        }
        catch (IllegalArgumentException ex)
        {
            // Expected exception
        }
    }

    /**
     * Test for removing a callback that is waiting to occur,
     * ie. a callback is removed which was a result of an evaluation and it
     * thus needs to be removed from the tree AND the current dispatch list.
     */
    public void testActiveCallbackRemove()
    {
        FilterValueSet spec = SupportFilterSpecBuilder.build(eventTypeOne, new Object[0]).getValueSet(null);
        final SupportFilterCallback callbackTwo = new SupportFilterCallback();

        // callback that removes another matching filter spec callback
        FilterCallback callbackOne = new FilterCallback()
        {
            public void matchFound(EventBean event)
            {
                log.debug(".matchFound Removing callbackTwo");
                filterService.remove(callbackTwo);
            }
        };

        filterService.add(spec, callbackOne);
        filterService.add(spec, callbackTwo);

        // send event
        filterService.evaluate(makeTypeOneEvent(1, "HELLO", false, 1));

        // Callback two MUST be invoked, was removed by callback one, but since the
        // callback invocation order should not matter, the second one MUST also execute
        assertEquals(1, callbackTwo.getAndResetCountInvoked());
    }

    private EventBean makeTypeOneEvent(int intPrimitive, String string, boolean boolPrimitive, double doubleBoxed)
    {
        SupportBean bean = new SupportBean();
        bean.setIntPrimitive(intPrimitive);
        bean.setString(string);
        bean.setBoolPrimitive(boolPrimitive);
        bean.setDoubleBoxed(doubleBoxed);
        return EventBeanFactory.createObject(bean);
    }

    private EventBean makeTypeTwoEvent(String myString, int myInt)
    {
        SupportBeanSimple bean = new SupportBeanSimple(myString, myInt);
        return EventBeanFactory.createObject(bean);
    }

    private static final Log log = LogFactory.getLog(TestFilterServiceImpl.class);
}
