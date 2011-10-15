/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.epl.join.pollindex;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventType;
import com.espertech.esper.epl.join.table.EventTable;
import com.espertech.esper.epl.join.table.PropertyIndexedEventTableFactory;
import com.espertech.esper.epl.join.table.UnindexedEventTableList;

import java.util.Arrays;
import java.util.List;

/**
 * Strategy for building an index out of poll-results knowing the properties to base the index on.
 */
public class PollResultIndexingStrategyIndex implements PollResultIndexingStrategy
{
    private final int streamNum;
    private final EventType eventType;
    private final String[] propertyNames;

    /**
     * Ctor.
     * @param streamNum is the stream number of the indexed stream
     * @param eventType is the event type of the indexed stream
     * @param propertyNames is the property names to be indexed
     */
    public PollResultIndexingStrategyIndex(int streamNum, EventType eventType, String[] propertyNames)
    {
        this.streamNum = streamNum;
        this.eventType = eventType;
        this.propertyNames = propertyNames;
    }

    public EventTable index(List<EventBean> pollResult, boolean isActiveCache)
    {
        if (!isActiveCache)
        {
            return new UnindexedEventTableList(pollResult);
        }
        PropertyIndexedEventTableFactory factory = new PropertyIndexedEventTableFactory(streamNum, eventType, propertyNames);
        EventTable table = factory.makeEventTable();
        table.add(pollResult.toArray(new EventBean[pollResult.size()]));
        return table;
    }

    public String toQueryPlan() {
        return this.getClass().getSimpleName() + " properties " + Arrays.toString(propertyNames);
    }
}
