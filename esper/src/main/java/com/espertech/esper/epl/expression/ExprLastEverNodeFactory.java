/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.epl.expression;

import com.espertech.esper.epl.agg.*;
import com.espertech.esper.epl.core.MethodResolutionService;

public class ExprLastEverNodeFactory implements AggregationMethodFactory
{
    private final Class childType;
    private final boolean hasFilter;

    public ExprLastEverNodeFactory(Class childType, boolean hasFilter) {
        this.childType = childType;
        this.hasFilter = hasFilter;
    }

    public Class getResultType()
    {
        return childType;
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
        return methodResolutionService.makeLastEverValueAggregator(agentInstanceIds, groupId, aggregationId, childType, hasFilter);
    }

    public AggregationMethodFactory getPrototypeAggregator() {
        return this;
    }
}