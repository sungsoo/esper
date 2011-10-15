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

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.regression.client.MyConcatAggregationFunction;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportBean_S0;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.util.ArrayAssertionUtil;
import com.espertech.esper.support.util.SupportUpdateListener;
import junit.framework.TestCase;

import java.util.Collection;

public class TestContextPartitionedAggregate extends TestCase {

    private EPServiceProvider epService;
    private SupportUpdateListener listener;

    public void setUp()
    {
        Configuration configuration = SupportConfigFactory.getConfiguration();
        configuration.addEventType("SupportBean", SupportBean.class);
        configuration.addEventType("SupportBean_S0", SupportBean_S0.class);
        configuration.getEngineDefaults().getLogging().setEnableExecutionDebug(true);
        configuration.addPlugInAggregationFunction("concat", MyConcatAggregationFunction.class.getName());
        configuration.addPlugInSingleRowFunction("toArray", this.getClass().getName(), "toArray");
        epService = EPServiceProviderManager.getDefaultProvider(configuration);
        epService.initialize();

        listener = new SupportUpdateListener();
    }

    public void testAccessOnly() {
        epService.getEPAdministrator().getConfiguration().addEventType("SupportBean", SupportBean.class);

        String eplContext = "@Name('CTX') create context SegmentedByString partition by string from SupportBean";
        epService.getEPAdministrator().createEPL(eplContext);

        String[] fieldsGrouped = "string,intPrimitive,col1".split(",");
        String eplGroupedAccess = "@Name('S2') context SegmentedByString select string,intPrimitive,window(longPrimitive) as col1 from SupportBean.win:keepall() sb group by intPrimitive";
        epService.getEPAdministrator().createEPL(eplGroupedAccess);
        epService.getEPAdministrator().getStatement("S2").addListener(listener);

        epService.getEPRuntime().sendEvent(makeEvent("G1", 1, 10L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsGrouped, new Object[]{"G1", 1, new Object[] {10L}});

        epService.getEPRuntime().sendEvent(makeEvent("G1", 2, 100L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsGrouped, new Object[]{"G1", 2, new Object[] {100L}});

        epService.getEPRuntime().sendEvent(makeEvent("G2", 1, 200L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsGrouped, new Object[]{"G2", 1, new Object[] {200L}});

        epService.getEPRuntime().sendEvent(makeEvent("G1", 1, 11L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsGrouped, new Object[]{"G1", 1, new Object[] {10L, 11L}});
    }

    public void testSegmentedSubqueryWithAggregation() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        String[] fields = new String[] {"string", "intPrimitive", "val0"};
        EPStatement stmtOne = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString " +
                "select string, intPrimitive, (select concat(p00) from SupportBean_S0.win:keepall() as s0 where sb.intPrimitive = s0.id) as val0 " +
                "from SupportBean as sb");
        stmtOne.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean_S0(10, "s1"));
        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fields, new Object[] {"G1", 10, null});
    }

    public void testGroupByEventPerGroupStream() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        String[] fieldsOne = "intPrimitive,count(*)".split(",");
        EPStatement stmtOne = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString select intPrimitive, count(*) from SupportBean group by intPrimitive");
        stmtOne.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {10, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {200, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {10, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 11));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {11, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {200, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {10, 1L});

        stmtOne.destroy();

        // add "string" : a context property
        String[] fieldsTwo = "string,intPrimitive,count(*)".split(",");
        EPStatement stmtTwo = epService.getEPAdministrator().createEPL("@Name('B') context SegmentedByString select string, intPrimitive, count(*) from SupportBean group by intPrimitive");
        stmtTwo.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G1", 10, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G2", 200, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G1", 10, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 11));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G1", 11, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G2", 200, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G2", 10, 1L});
    }

    public void testGroupByEventPerGroupBatchContextProp() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        String[] fieldsOne = "intPrimitive,count(*)".split(",");
        EPStatement stmtOne = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString select intPrimitive, count(*) from SupportBean.win:length_batch(2) group by intPrimitive order by intPrimitive asc");
        stmtOne.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        assertFalse(listener.isInvoked());
        
        epService.getEPRuntime().sendEvent(new SupportBean("G1", 11));
        ArrayAssertionUtil.assertProps(listener.getLastNewData()[0], fieldsOne, new Object[] {10, 1L});
        ArrayAssertionUtil.assertProps(listener.getAndResetLastNewData()[1], fieldsOne, new Object[] {11, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        assertFalse(listener.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[] {200, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.getLastNewData()[0], fieldsOne, new Object[] {10, 2L});
        ArrayAssertionUtil.assertProps(listener.getAndResetLastNewData()[1], fieldsOne, new Object[] {11, 0L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 10));
        epService.getEPRuntime().sendEvent(new SupportBean("G2", 10));
        ArrayAssertionUtil.assertProps(listener.getLastNewData()[0], fieldsOne, new Object[] {10, 2L});
        ArrayAssertionUtil.assertProps(listener.getAndResetLastNewData()[1], fieldsOne, new Object[] {200, 0L});

        stmtOne.destroy();

        // add "string" : add context property
        String[] fieldsTwo = "string,intPrimitive,count(*)".split(",");
        EPStatement stmtTwo = epService.getEPAdministrator().createEPL("@Name('B') context SegmentedByString select string, intPrimitive, count(*) from SupportBean.win:length_batch(2) group by intPrimitive order by string, intPrimitive asc");
        stmtTwo.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        assertFalse(listener.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 11));
        ArrayAssertionUtil.assertProps(listener.getLastNewData()[0], fieldsTwo, new Object[] {"G1", 10, 1L});
        ArrayAssertionUtil.assertProps(listener.getAndResetLastNewData()[1], fieldsTwo, new Object[] {"G1", 11, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        assertFalse(listener.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[] {"G2", 200, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.getLastNewData()[0], fieldsTwo, new Object[] {"G1", 10, 2L});
        ArrayAssertionUtil.assertProps(listener.getAndResetLastNewData()[1], fieldsTwo, new Object[] {"G1", 11, 0L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 10));
        epService.getEPRuntime().sendEvent(new SupportBean("G2", 10));
        ArrayAssertionUtil.assertProps(listener.getLastNewData()[0], fieldsTwo, new Object[] {"G2", 10, 2L});
        ArrayAssertionUtil.assertProps(listener.getAndResetLastNewData()[1], fieldsTwo, new Object[] {"G2", 200, 0L});
    }

    public void testGroupByEventPerGroupWithAccess() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        String[] fieldsOne = "intPrimitive,col1,col2,col3".split(",");
        EPStatement stmtOne = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString " +
                "select intPrimitive, count(*) as col1, toArray(window(*).selectFrom(v=>v.longPrimitive)) as col2, first().longPrimitive as col3 " +
                "from SupportBean.win:keepall() as sb " +
                "group by intPrimitive order by intPrimitive asc");
        stmtOne.addListener(listener);

        epService.getEPRuntime().sendEvent(makeEvent("G1", 10, 200L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 1L, new Object[] {200L}, 200L});

        epService.getEPRuntime().sendEvent(makeEvent("G1", 10, 300L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 2L, new Object[] {200L, 300L}, 200L});

        epService.getEPRuntime().sendEvent(makeEvent("G2", 10, 1000L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 1L, new Object[] {1000L}, 1000L});

        epService.getEPRuntime().sendEvent(makeEvent("G2", 10, 1010L));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 2L, new Object[] {1000L, 1010L}, 1000L});

        stmtOne.destroy();
    }

    public void testGroupByEventForAll() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        // test aggregation-only (no access)
        String[] fieldsOne = "col1".split(",");
        EPStatement stmtOne = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString " +
                "select sum(intPrimitive) as col1 " +
                "from SupportBean");
        stmtOne.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 3));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{3});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 2));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{2});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 4));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{7});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 1));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{3});

        epService.getEPRuntime().sendEvent(new SupportBean("G3", -1));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{-1});

        stmtOne.destroy();

        // test mixed with access
        String[] fieldsTwo = "col1,col2".split(",");
        EPStatement stmtTwo = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString " +
                "select sum(intPrimitive) as col1, toArray(window(*).selectFrom(v=>v.intPrimitive)) as col2 " +
                "from SupportBean.win:keepall()");
        stmtTwo.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 8));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[]{8, new Object[] {8}});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 5));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[]{5, new Object[] {5}});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 1));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[]{9, new Object[] {8, 1}});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 2));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsTwo, new Object[]{7, new Object[] {5, 2}});

        stmtTwo.destroy();

        // test only access
        String[] fieldsThree = "col1".split(",");
        EPStatement stmtThree = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString " +
                "select toArray(window(*).selectFrom(v=>v.intPrimitive)) as col1 " +
                "from SupportBean.win:keepall()");
        stmtThree.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 8));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsThree, new Object[]{new Object[] {8}});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 5));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsThree, new Object[]{new Object[] {5}});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 1));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsThree, new Object[]{new Object[] {8, 1}});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 2));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsThree, new Object[]{new Object[] {5, 2}});

        stmtThree.destroy();
    }

    public void testGroupByEventPerGroupUnidirectionalJoin() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        String[] fieldsOne = "intPrimitive,col1".split(",");
        EPStatement stmtOne = epService.getEPAdministrator().createEPL("@Name('A') context SegmentedByString " +
                "select intPrimitive, count(*) as col1 " +
                "from SupportBean unidirectional, SupportBean_S0.win:keepall() " +
                "group by intPrimitive order by intPrimitive asc");
        stmtOne.addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(1));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(2));
        assertFalse(listener.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(3));

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 3L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 20));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(4));
        assertFalse(listener.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 20));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{20, 1L});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(5));

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 20));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{20, 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), fieldsOne, new Object[]{10, 5L});

        stmtOne.destroy();
    }

    private SupportBean makeEvent(String string, int intPrimitive, long longPrimitive) {
        SupportBean bean = new SupportBean(string, intPrimitive);
        bean.setLongPrimitive(longPrimitive);
        return bean;
    }

    public static Object toArray(Collection in) {
        return in.toArray();
    }
}
