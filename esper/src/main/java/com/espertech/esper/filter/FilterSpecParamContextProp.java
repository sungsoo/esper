/**************************************************************************************
 * Copyright (C) 2008 EsperTech, Inc. All rights reserved.                            *
 * http://esper.codehaus.org                                                          *
 * http://www.espertech.com                                                           *
 * ---------------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the GPL license       *
 * a copy of which has been included with this distribution in the license.txt file.  *
 **************************************************************************************/
package com.espertech.esper.filter;

import com.espertech.esper.client.EventPropertyGetter;
import com.espertech.esper.epl.expression.ExprEvaluatorContext;
import com.espertech.esper.pattern.MatchedEventMap;
import com.espertech.esper.util.SimpleNumberCoercer;

/**
 * This class represents a filter parameter containing a reference to a context property.
 */
public final class FilterSpecParamContextProp extends FilterSpecParam
{
    private final EventPropertyGetter getter;
    private transient final SimpleNumberCoercer numberCoercer;

    public FilterSpecParamContextProp(String propertyName, FilterOperator filterOperator, EventPropertyGetter getter, SimpleNumberCoercer numberCoercer) {
        super(propertyName, filterOperator);
        this.getter = getter;
        this.numberCoercer = numberCoercer;
    }

    public Object getFilterValue(MatchedEventMap matchedEvents, ExprEvaluatorContext evaluatorContext) {
        if (evaluatorContext.getContextProperties() == null) {
            return null;
        }
        Object result = getter.get(evaluatorContext.getContextProperties());

        if (numberCoercer == null) {
            return result;
        }
        return numberCoercer.coerceBoxed((Number) result);
    }

    public int getFilterHash() {
        return super.hashCode();
    }
}
