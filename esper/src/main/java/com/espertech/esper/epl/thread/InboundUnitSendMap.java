package com.espertech.esper.epl.thread;

import com.espertech.esper.core.EPRuntimeImpl;
import com.espertech.esper.core.EPServicesContext;
import com.espertech.esper.client.EventBean;

import java.util.Map;

public class InboundUnitSendMap implements InboundUnitRunnable
{
    private final Map map;
    private final String eventTypeName;
    private final EPServicesContext services;
    private final EPRuntimeImpl runtime;

    public InboundUnitSendMap(Map map, String eventTypeName, EPServicesContext services, EPRuntimeImpl runtime)
    {
        this.eventTypeName = eventTypeName;
        this.map = map;
        this.services = services;
        this.runtime = runtime;
    }

    public void run()
    {
        EventBean eventBean = services.getEventAdapterService().adapterForMap(map, eventTypeName);
        runtime.processWrappedEvent(eventBean);
    }    
}
