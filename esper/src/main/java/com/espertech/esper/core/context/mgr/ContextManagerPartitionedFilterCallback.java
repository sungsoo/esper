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

package com.espertech.esper.core.context.mgr;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventPropertyGetter;
import com.espertech.esper.collection.MultiKeyUntyped;
import com.espertech.esper.core.context.util.AgentInstanceContext;
import com.espertech.esper.core.service.EPServicesContext;
import com.espertech.esper.core.service.EPStatementHandleCallback;
import com.espertech.esper.epl.spec.ContextDetailPartitionItem;
import com.espertech.esper.filter.FilterHandleCallback;
import com.espertech.esper.filter.FilterService;
import com.espertech.esper.filter.FilterValueSet;

import java.util.Collection;

public class ContextManagerPartitionedFilterCallback implements FilterHandleCallback {

    private final AgentInstanceContext agentInstanceContextCreateContext;
    private final EventPropertyGetter[] getters;
    private final ContextManagerPartitionedInstanceCreateCallback callback;
    private final EPStatementHandleCallback filterHandle;

    public ContextManagerPartitionedFilterCallback(EPServicesContext servicesContext, AgentInstanceContext agentInstanceContextCreateContext, ContextDetailPartitionItem partitionItem, ContextManagerPartitionedInstanceCreateCallback callback) {
        this.agentInstanceContextCreateContext = agentInstanceContextCreateContext;
        this.callback = callback;

        filterHandle = new EPStatementHandleCallback(agentInstanceContextCreateContext.getEpStatementAgentInstanceHandle(), this);

        getters = new EventPropertyGetter[partitionItem.getPropertyNames().size()];
        for (int i = 0; i < partitionItem.getPropertyNames().size(); i++) {
            String propertyName = partitionItem.getPropertyNames().get(i);
            EventPropertyGetter getter = partitionItem.getFilterSpecCompiled().getFilterForEventType().getGetter(propertyName);
            getters[i] = getter;
        }

        FilterValueSet filterValueSet = partitionItem.getFilterSpecCompiled().getValueSet(null, null, null);
        servicesContext.getFilterService().add(filterValueSet, filterHandle);
    }

    public void matchFound(EventBean event, Collection<FilterHandleCallback> allStmtMatches) {
        Object key;
        if (getters.length > 1) {
            Object[] keys = new Object[getters.length];
            for (int i = 0; i < keys.length; i++) {
                 keys[i] = getters[i].get(event);
            }
            key = new MultiKeyUntyped(keys);
        }
        else {
            key = getters[0].get(event);
        }

        callback.create(key, event);
    }

    public boolean isSubSelect() {
        return false;
    }

    public String getStatementId() {
        return agentInstanceContextCreateContext.getStatementContext().getStatementId();
    }

    public void destroy(FilterService filterService) {
        filterService.remove(filterHandle);
    }
}
