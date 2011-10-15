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
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportBean_S0;
import com.espertech.esper.support.bean.SupportBean_S1;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.util.ArrayAssertionUtil;
import com.espertech.esper.support.util.SupportUpdateListener;
import junit.framework.TestCase;

public class TestContextPartitionedNamedWindow extends TestCase {

    private EPServiceProvider epService;
    private SupportUpdateListener listenerSelect;
    private SupportUpdateListener listenerNamedWindow;

    public void setUp()
    {
        Configuration configuration = SupportConfigFactory.getConfiguration();
        configuration.addEventType("SupportBean", SupportBean.class);
        configuration.addEventType("SupportBean_S0", SupportBean_S0.class);
        configuration.addEventType("SupportBean_S1", SupportBean_S1.class);
        configuration.getEngineDefaults().getLogging().setEnableExecutionDebug(true);
        epService = EPServiceProviderManager.getDefaultProvider(configuration);
        epService.initialize();

        listenerSelect = new SupportUpdateListener();
        listenerNamedWindow = new SupportUpdateListener();
    }

    public void testNWFireAndForgetInvalid() {
        epService.getEPAdministrator().createEPL("create context SegmentedByString partition by string from SupportBean");

        epService.getEPAdministrator().createEPL("context SegmentedByString create window MyWindow.win:keepall() as SupportBean");
        epService.getEPAdministrator().createEPL("context SegmentedByString insert into MyWindow select * from SupportBean");

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 0));

        try {
            epService.getEPRuntime().executeQuery("select * from MyWindow");
        }
        catch (EPException ex) {
            String expected = "Error executing statement: Named window 'MyWindow' is associated to context 'SegmentedByString' that is not available for querying [select * from MyWindow]";
            assertEquals(expected, ex.getMessage());
        }
    }

    public void testSegmentedNWConsumeAll() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        EPStatement stmtNamedWindow = epService.getEPAdministrator().createEPL("@Name('named window') context SegmentedByString create window MyWindow.std:lastevent() as SupportBean");
        stmtNamedWindow.addListener(listenerNamedWindow);
        epService.getEPAdministrator().createEPL("@Name('insert') insert into MyWindow select * from SupportBean");

        EPStatement stmtSelect = epService.getEPAdministrator().createEPL("@Name('select') select * from MyWindow");
        stmtSelect.addListener(listenerSelect);

        String[] fields = new String[] {"string", "intPrimitive"};
        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fields, new Object[]{"G1", 10});
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[] {"G1", 10});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 20));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fields, new Object[]{"G2", 20});
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fields, new Object[] {"G2", 20});

        stmtSelect.destroy();

        // Out-of-context consumer not initialized
        EPStatement stmtSelectCount = epService.getEPAdministrator().createEPL("@Name('select') select count(*) as cnt from MyWindow");
        stmtSelectCount.addListener(listenerSelect);
        ArrayAssertionUtil.assertProps(stmtSelectCount.iterator().next(), "cnt".split(","), new Object[] {0L});
    }

    public void testSegmentedNWConsumeSameContext() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString partition by string from SupportBean");

        EPStatement stmtNamedWindow = epService.getEPAdministrator().createEPL("@Name('named window') context SegmentedByString create window MyWindow.win:keepall() as SupportBean");
        stmtNamedWindow.addListener(listenerNamedWindow);
        epService.getEPAdministrator().createEPL("@Name('insert') insert into MyWindow select * from SupportBean");

        String[] fieldsNW = new String[] {"string", "intPrimitive"};
        String[] fieldsCnt = new String[] {"string", "cnt"};
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL("@Name('select') context SegmentedByString select string, count(*) as cnt from MyWindow group by string");
        stmtSelect.addListener(listenerSelect);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 10));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G1", 10});
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsCnt, new Object[] {"G1", 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 20));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G2", 20});
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsCnt, new Object[] {"G2", 1L});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 11));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G1", 11});
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsCnt, new Object[] {"G1", 2L});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 21));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G2", 21});
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsCnt, new Object[] {"G2", 2L});

        stmtSelect.destroy();

        // In-context consumer not initialized
        EPStatement stmtSelectCount = epService.getEPAdministrator().createEPL("@Name('select') context SegmentedByString select count(*) as cnt from MyWindow");
        stmtSelectCount.addListener(listenerSelect);
        try {
            // ArrayAssertionUtil.assertProps(stmtSelectCount.iterator().next(), "cnt".split(","), new Object[] {0L});
            stmtSelectCount.iterator();
        }
        catch (UnsupportedOperationException ex) {
            assertEquals("Iterator not supported on statements that have a context attached", ex.getMessage());
        }
    }

    public void testOnDeleteAndUpdate() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString " +
                "partition by string from SupportBean, p00 from SupportBean_S0, p10 from SupportBean_S1");

        String[] fieldsNW = new String[] {"string", "intPrimitive"};
        epService.getEPAdministrator().createEPL("@Name('named window') context SegmentedByString create window MyWindow.win:keepall() as SupportBean");
        epService.getEPAdministrator().createEPL("@Name('insert') insert into MyWindow select * from SupportBean");

        epService.getEPAdministrator().createEPL("@Name('on-merge') context SegmentedByString select irstream * from MyWindow").addListener(listenerSelect);

        // Delete testing
        EPStatement stmtDelete = epService.getEPAdministrator().createEPL("@Name('on-merge') context SegmentedByString on SupportBean_S0 delete from MyWindow");

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 1));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G1", 1});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G0"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G2"));
        assertFalse(listenerSelect.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G1"));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetOldAndReset(), fieldsNW, new Object[]{"G1", 1});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 20));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G2", 20});

        epService.getEPRuntime().sendEvent(new SupportBean("G3", 3));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G3", 3});

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 21));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G2", 21});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G2"));
        ArrayAssertionUtil.assertPropsPerRow(listenerSelect.getLastOldData(), fieldsNW, new Object[][]{{"G2", 20}, {"G2", 21}});
        listenerSelect.reset();

        stmtDelete.destroy();

        // update testing
        EPStatement stmtUpdate = epService.getEPAdministrator().createEPL("@Name('on-merge') context SegmentedByString on SupportBean_S0 update MyWindow set intPrimitive = intPrimitive + 1");

        epService.getEPRuntime().sendEvent(new SupportBean("G4", 4));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G4", 4});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G0"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G1"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G2"));
        assertFalse(listenerSelect.isInvoked());

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G4"));
        ArrayAssertionUtil.assertProps(listenerSelect.getLastNewData()[0], fieldsNW, new Object[]{"G4", 5});
        ArrayAssertionUtil.assertProps(listenerSelect.getLastOldData()[0], fieldsNW, new Object[]{"G4", 4});
        listenerSelect.reset();

        epService.getEPRuntime().sendEvent(new SupportBean("G5", 5));
        ArrayAssertionUtil.assertProps(listenerSelect.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G5", 5});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G5"));
        ArrayAssertionUtil.assertProps(listenerSelect.getLastNewData()[0], fieldsNW, new Object[]{"G5", 6});
        ArrayAssertionUtil.assertProps(listenerSelect.getLastOldData()[0], fieldsNW, new Object[]{"G5", 5});
        listenerSelect.reset();

        stmtUpdate.destroy();
    }

    public void testSegmentedOnMergeUpdateSubq() {
        epService.getEPAdministrator().createEPL("@Name('context') create context SegmentedByString " +
                "partition by string from SupportBean, p00 from SupportBean_S0, p10 from SupportBean_S1");

        EPStatement stmtNamedWindow = epService.getEPAdministrator().createEPL("@Name('named window') context SegmentedByString create window MyWindow.win:keepall() as SupportBean");
        stmtNamedWindow.addListener(listenerNamedWindow);
        epService.getEPAdministrator().createEPL("@Name('insert') insert into MyWindow select * from SupportBean");

        String[] fieldsNW = new String[] {"string", "intPrimitive"};
        EPStatement stmtSelect = epService.getEPAdministrator().createEPL("@Name('on-merge') context SegmentedByString " +
                "on SupportBean_S0 " +
                "merge MyWindow " +
                "when matched then " +
                "  update set intPrimitive = (select id from SupportBean_S1.std:lastevent())");
        stmtSelect.addListener(listenerSelect);

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 1));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G1", 1});

        epService.getEPRuntime().sendEvent(new SupportBean_S1(99, "G1"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G1"));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.getLastNewData()[0], fieldsNW, new Object[]{"G1", 99});
        ArrayAssertionUtil.assertProps(listenerNamedWindow.getLastOldData()[0], fieldsNW, new Object[]{"G1", 1});
        listenerNamedWindow.reset();

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 2));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G2", 2});

        epService.getEPRuntime().sendEvent(new SupportBean_S1(98, "Gx"));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "G2"));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.getLastNewData()[0], fieldsNW, new Object[]{"G2", 2});
        ArrayAssertionUtil.assertProps(listenerNamedWindow.getLastOldData()[0], fieldsNW, new Object[]{"G2", 2});
        listenerNamedWindow.reset();

        epService.getEPRuntime().sendEvent(new SupportBean("G3", 3));
        ArrayAssertionUtil.assertProps(listenerNamedWindow.assertOneGetNewAndReset(), fieldsNW, new Object[]{"G3", 3});

        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "Gx"));
        assertFalse(listenerNamedWindow.isInvoked());
    }
}
