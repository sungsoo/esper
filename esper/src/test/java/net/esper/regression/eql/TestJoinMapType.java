package net.esper.regression.eql;

import junit.framework.TestCase;
import net.esper.client.EPServiceProvider;
import net.esper.client.EPServiceProviderManager;
import net.esper.client.EPStatement;
import net.esper.client.Configuration;
import net.esper.support.util.SupportUpdateListener;
import net.esper.support.bean.SupportMarketDataBean;
import net.esper.support.bean.SupportBean;
import net.esper.support.client.SupportConfigFactory;
import net.esper.event.EventBean;

import java.util.Map;
import java.util.HashMap;

public class TestJoinMapType extends TestCase
{
    private EPServiceProvider epService;
    private SupportUpdateListener listener;

    public void setUp()
    {
        Map<String, Class> typeInfo = new HashMap<String, Class>();
        typeInfo.put("id", String.class);
        typeInfo.put("p00", int.class);
        
        Configuration config = SupportConfigFactory.getConfiguration();
        config.addEventTypeAlias("MapS0", typeInfo);
        config.addEventTypeAlias("MapS1", typeInfo);

        epService = EPServiceProviderManager.getDefaultProvider(config);
        epService.initialize();
        listener = new SupportUpdateListener();
    }

    public void testJoinMapEvent()
    {
        String joinStatement = "select S0.id, S1.id, S0.p00, S1.p00 from MapS0 as S0, MapS1 as S1" +
                " where S0.id = S1.id";

        EPStatement stmt = epService.getEPAdministrator().createEQL(joinStatement);
        stmt.addListener(listener);

        sendMapEvent("MapS0", "a", 1);
        assertFalse(listener.isInvoked());
        
        sendMapEvent("MapS1", "a", 2);
        EventBean event = listener.assertOneGetNewAndReset();
        assertEquals("a", event.get("S0.id"));
        assertEquals("a", event.get("S1.id"));
        assertEquals(1, event.get("S0.p00"));
        assertEquals(2, event.get("S1.p00"));

        sendMapEvent("MapS1", "b", 3);
        sendMapEvent("MapS0", "c", 4);
        assertFalse(listener.isInvoked());
    }

    public void testJoinMapEventNotUnique()
    {
        // Test for Esper-122 
        String joinStatement = "select S0.id, S1.id, S0.p00, S1.p00 from MapS0 as S0, MapS1 as S1" +
                " where S0.id = S1.id";

        EPStatement stmt = epService.getEPAdministrator().createEQL(joinStatement);
        stmt.addListener(listener);

        for (int i = 0; i < 100; i++)
        {
            if (i % 2 == 1)
            {
                sendMapEvent("MapS0", "a", 1);
            }
            else
            {
                sendMapEvent("MapS1", "a", 1);
            }
        }
    }

    public void testJoinWrapperEventNotUnique()
    {
        // Test for Esper-122
        epService.getEPAdministrator().createEQL("insert into S0 select 's0' as stream, * from " + SupportBean.class.getName());
        epService.getEPAdministrator().createEQL("insert into S1 select 's1' as stream, * from " + SupportBean.class.getName());
        String joinStatement = "select * from S0 as a, S1 as b where a.intBoxed = b.intBoxed";

        EPStatement stmt = epService.getEPAdministrator().createEQL(joinStatement);
        stmt.addListener(listener);

        for (int i = 0; i < 100; i++)
        {
            epService.getEPRuntime().sendEvent(new SupportBean());
        }
    }

    private void sendMapEvent(String alias, String id, int p00)
    {
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("id", id);
        event.put("p00", p00);
        epService.getEPRuntime().sendEvent(event, alias);
    }
}
