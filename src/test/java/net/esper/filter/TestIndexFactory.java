package net.esper.filter;

import junit.framework.TestCase;
import net.esper.support.bean.SupportBean;
import net.esper.event.EventType;
import net.esper.event.EventTypeFactory;

public class TestIndexFactory extends TestCase
{
    EventType eventType;

    public void setUp()
    {
        eventType = EventTypeFactory.getInstance().createBeanType(SupportBean.class);
    }

    public void testCreateIndex()
    {
        // Create a "greater" index
        FilterParamIndex index = IndexFactory.createIndex(eventType, "intPrimitive", FilterOperator.GREATER);

        assertTrue(index != null);
        assertTrue(index instanceof FilterParamIndexCompare);
        assertTrue(index.getPropertyName().equals("intPrimitive"));
        assertTrue(index.getFilterOperator() == FilterOperator.GREATER);

        // Create an "equals" index
        index = IndexFactory.createIndex(eventType, "string", FilterOperator.EQUAL);

        assertTrue(index != null);
        assertTrue(index instanceof FilterParamIndexEquals);
        assertTrue(index.getPropertyName().equals("string"));
        assertTrue(index.getFilterOperator() == FilterOperator.EQUAL);

        // Create a range index
        index = IndexFactory.createIndex(eventType, "doubleBoxed", FilterOperator.RANGE_CLOSED);
        assertTrue(index instanceof FilterParamIndexRange);
    }
}

