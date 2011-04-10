package com.espertech.esper.support.epl;

import com.espertech.esper.client.EventType;
import com.espertech.esper.epl.join.exec.base.ExecNode;
import com.espertech.esper.epl.join.plan.QueryPlanNode;
import com.espertech.esper.epl.join.table.EventTable;
import com.espertech.esper.epl.join.table.HistoricalStreamIndexList;
import com.espertech.esper.util.IndentWriter;
import com.espertech.esper.epl.virtualdw.VirtualDWView;
import com.espertech.esper.view.Viewable;

import java.util.HashSet;
import java.util.Map;

public class SupportQueryPlanNode extends QueryPlanNode
{
    private String id;

    public SupportQueryPlanNode(String id)
    {
        this.id = id;
    }

    public ExecNode makeExec(Map<String, EventTable>[] indexPerStream, EventType[] streamTypes, Viewable[] streamViews, HistoricalStreamIndexList[] historicalStreamIndexLists, VirtualDWView[] viewExternal)
    {
        return new SupportQueryExecNode(id);
    }

    protected void print(IndentWriter writer)
    {
        writer.println(this.getClass().getName());
    }

    public void addIndexes(HashSet<String> usedIndexes) {        
    }
}
