package net.esper.regression.view;

import junit.framework.TestCase;
import net.esper.client.*;
import net.esper.support.util.SupportUpdateListener;
import net.esper.support.util.ArrayAssertionUtil;
import net.esper.support.bean.SupportBean;
import net.esper.support.bean.SupportBeanComplexProps;
import net.esper.support.client.SupportConfigFactory;
import net.esper.event.EventBean;

public class TestArrayExpression extends TestCase
{
    // for use in testing a static method accepting array parameters
    private static Integer[] callbackInts;
    private static String[] callbackStrings;
    private static Object[] callbackObjects;

    private EPServiceProvider epService;

    protected void setUp()
    {
        epService = EPServiceProviderManager.getDefaultProvider(SupportConfigFactory.getConfiguration());
        epService.initialize();
    }

    public void testArrayExpressions()
    {
        String stmtText = "select {'a', 'b'} as stringArray," +
                              "{} as emptyArray," +
                              "{1} as oneEleArray," +
                              "{1,2,3} as intArray," +
                              "{1,null} as intNullArray," +
                              "{1L,10L} as longArray," +
                              "{'a',1, 1e20} as mixedArray," +
                              "{1, 1.1d, 1e20} as doubleArray," +
                              "{5, 6L} as intLongArray," +
                              "{null} as nullArray," +
                              TestArrayExpression.class.getName() + ".doIt({'a'}, {1}, {1, 'd', null, true}) as func," +
                              "{true, false} as boolArray," +
                              "{intPrimitive} as dynIntArr," +
                              "{intPrimitive, longPrimitive} as dynLongArr," +
                              "{intPrimitive, string} as dynMixedArr," +
                              "{intPrimitive, intPrimitive * 2, intPrimitive * 3} as dynCalcArr," +
                              "{longBoxed, doubleBoxed * 2, string || 'a'} as dynCalcArrNulls" +
                              " from " + SupportBean.class.getName();

        EPStatement stmt = epService.getEPAdministrator().createEQL(stmtText);
        SupportUpdateListener listener = new SupportUpdateListener();
        stmt.addListener(listener);

        SupportBean bean = new SupportBean("a", 10);
        bean.setLongPrimitive(999);
        epService.getEPRuntime().sendEvent(bean);

        EventBean event = listener.assertOneGetNewAndReset();
        ArrayAssertionUtil.assertEqualsExactOrder(new String[] {"a", "b"}, (String[]) event.get("stringArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Object[0], (Object[]) event.get("emptyArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Integer[] {1}, (Integer[]) event.get("oneEleArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Integer[] {1,2,3}, (Integer[]) event.get("intArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Integer[] {1,null}, (Integer[]) event.get("intNullArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Long[] {1L,10L}, (Long[]) event.get("longArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Object[] {"a", 1, 1e20}, (Object[]) event.get("mixedArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Double[] {1d, 1.1,1e20}, (Double[]) event.get("doubleArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Long[] {5L, 6L}, (Long[]) event.get("intLongArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Object[] {null}, (Object[]) event.get("nullArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new String[] {"a", "b"}, (String[]) event.get("func"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Boolean[] {true, false}, (Boolean[]) event.get("boolArray"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Integer[] {10}, (Integer[]) event.get("dynIntArr"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Long[] {10L, 999L}, (Long[]) event.get("dynLongArr"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Object[] {10, "a"}, (Object[]) event.get("dynMixedArr"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Integer[] {10, 20, 30}, (Integer[]) event.get("dynCalcArr"));
        ArrayAssertionUtil.assertEqualsExactOrder(new Object[] {null, null, "aa"}, (Object[]) event.get("dynCalcArrNulls"));

        // assert function parameters
        ArrayAssertionUtil.assertEqualsExactOrder(new Integer[] {1}, callbackInts);
        ArrayAssertionUtil.assertEqualsExactOrder(new String[] {"a"}, callbackStrings);
        ArrayAssertionUtil.assertEqualsExactOrder(new Object[] {1, "d", null, true}, callbackObjects);
    }

    public void testComplexTypes()
    {
        String stmtText = "select {arrayProperty, nested} as field" +
                              " from " + SupportBeanComplexProps.class.getName();

        EPStatement stmt = epService.getEPAdministrator().createEQL(stmtText);
        SupportUpdateListener listener = new SupportUpdateListener();
        stmt.addListener(listener);

        SupportBeanComplexProps bean = SupportBeanComplexProps.makeDefaultBean();
        epService.getEPRuntime().sendEvent(bean);

        EventBean event = listener.assertOneGetNewAndReset();
        Object[] arr = (Object[]) event.get("field");
        assertSame(bean.getArrayProperty(), arr[0]);
        assertSame(bean.getNested(), arr[1]);
    }

    // for testing EQL static method call
    public static String[] doIt(String[] strings, Integer[] ints, Object[] objects)
    {
        callbackInts = ints;
        callbackStrings = strings;
        callbackObjects = objects;
        return new String[] {"a", "b"};
    }
}
