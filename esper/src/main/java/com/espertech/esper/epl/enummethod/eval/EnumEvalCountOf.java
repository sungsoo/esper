package com.espertech.esper.epl.enummethod.eval;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.epl.expression.ExprEvaluatorContext;

import java.util.Collection;

public class EnumEvalCountOf implements EnumEval {

    private EventBean[] events;
    
    public EnumEvalCountOf(int numEvents) {
        events = new EventBean[numEvents];
    }

    public EventBean[] getEventsPrototype() {
        return events;
    }

    public Object evaluateEnumMethod(Collection target, boolean isNewData, ExprEvaluatorContext context) {
        return target.size();
    }
}
