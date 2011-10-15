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

package com.espertech.esper.core.context.stmt;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.collection.MultiKeyUntyped;
import com.espertech.esper.core.context.stmt.AIRegistryAggregation;
import com.espertech.esper.epl.agg.AggregationService;
import com.espertech.esper.epl.expression.ExprEvaluatorContext;

import java.util.Collection;

public class AIRegistryAggregationSingle implements AIRegistryAggregation, AggregationService {
    private AggregationService service;

    public AIRegistryAggregationSingle() {
    }

    public void assignService(int serviceId, AggregationService aggregationService) {
        service = aggregationService;
    }

    public void deassignService(int serviceId) {
        service = null;
    }

    public int getInstanceCount() {
        return service == null ? 0 : 1;
    }

    public void applyEnter(EventBean[] eventsPerStream, MultiKeyUntyped optionalGroupKeyPerRow, ExprEvaluatorContext exprEvaluatorContext) {
        service.applyEnter(eventsPerStream, optionalGroupKeyPerRow, exprEvaluatorContext);
    }

    public void applyLeave(EventBean[] eventsPerStream, MultiKeyUntyped optionalGroupKeyPerRow, ExprEvaluatorContext exprEvaluatorContext) {
        service.applyLeave(eventsPerStream, optionalGroupKeyPerRow, exprEvaluatorContext);
    }

    public void setCurrentAccess(MultiKeyUntyped groupKey, int[] agentInstanceIds) {
        service.setCurrentAccess(groupKey, agentInstanceIds);
    }

    public void clearResults(ExprEvaluatorContext exprEvaluatorContext) {
        service.clearResults(exprEvaluatorContext);
    }

    public Object getValue(int column, int[] agentInstanceIds) {
        return service.getValue(column, agentInstanceIds);
    }

    public Collection<EventBean> getCollection(int column, ExprEvaluatorContext context) {
        return service.getCollection(column, context);
    }

    public EventBean getEventBean(int column, ExprEvaluatorContext context) {
        return service.getEventBean(column, context);
    }
}
