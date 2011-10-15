/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.epl.expression;

import com.espertech.esper.epl.agg.AggregationAccessor;
import com.espertech.esper.epl.agg.AggregationMethod;
import com.espertech.esper.epl.agg.AggregationMethodFactory;
import com.espertech.esper.epl.agg.AggregationSpec;
import com.espertech.esper.epl.core.MethodResolutionService;

public class ExprMedianNodeFactory implements AggregationMethodFactory
{
    private final boolean isDistinct;
    private final Class aggregatedValueType;
    private final boolean hasFilter;

    public ExprMedianNodeFactory(boolean isDistinct, Class aggregatedValueType, boolean hasFilter)
    {
        this.isDistinct = isDistinct;
        this.aggregatedValueType = aggregatedValueType;
        this.hasFilter = hasFilter;
    }

    public Class getResultType()
    {
        return Double.class;
    }

    public AggregationSpec getSpec(boolean isMatchRecognize)
    {
        return null;
    }

    public AggregationAccessor getAccessor()
    {
        throw new UnsupportedOperationException();
    }

    public AggregationMethod make(MethodResolutionService methodResolutionService, int[] agentInstanceIds, int groupId, int aggregationId) {
        AggregationMethod method = methodResolutionService.makeMedianAggregator(agentInstanceIds, groupId, aggregationId, hasFilter);
        if (!isDistinct) {
            return method;
        }
        return methodResolutionService.makeDistinctAggregator(agentInstanceIds, groupId, aggregationId, method, aggregatedValueType, hasFilter);
    }

    public AggregationMethodFactory getPrototypeAggregator() {
        return this;
    }
}