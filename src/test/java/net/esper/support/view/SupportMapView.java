package net.esper.support.view;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.esper.event.EventBean;
import net.esper.event.EventTypeFactory;

public class SupportMapView extends SupportBaseView
{
    private static List<SupportMapView> instances = new LinkedList<SupportMapView>();

    public SupportMapView()
    {
        instances.add(this);
    }

    public void update(EventBean[] newData, EventBean[] oldData)
    {
        super.setInvoked(true);
        this.lastNewData = newData;
        this.lastOldData = oldData;

        updateChildren(newData, oldData);
    }

    public SupportMapView(Map<String, Class> eventTypeMap)
    {
        super(EventTypeFactory.getInstance().createMapType(eventTypeMap));
        instances.add(this);
    }

    public static List<SupportMapView> getInstances()
    {
        return instances;
    }
}
