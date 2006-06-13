package net.esper.regression.view;

import junit.framework.TestCase;
import net.esper.client.EPException;
import net.esper.client.EPServiceProvider;
import net.esper.client.EPServiceProviderManager;
import net.esper.client.EPStatementException;
import net.esper.support.bean.SupportBean;
import net.esper.support.bean.SupportBean_N;
import net.esper.eql.parse.ASTFilterSpecValidationException;
import net.esper.eql.parse.EPStatementSyntaxException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TestInvalidView extends TestCase
{
    private final String EVENT_NUM = SupportBean_N.class.getName();
    private final String EVENT_ALLTYPES = SupportBean.class.getName();

    private EPServiceProvider epService;

    public void setUp()
    {
        epService = EPServiceProviderManager.getDefaultProvider();
        epService.initialize();
    }

    public void testSyntaxException()
    {
        String exceptionText = getSyntaxExceptionView("select * * from " + EVENT_NUM);
        assertEquals("expecting \"from\", found '*' near line 1, column 10 [select * * from net.esper.support.bean.SupportBean_N]", exceptionText);
    }

    public void testStatementException() throws Exception
    {
        String exceptionText = null;

        // class not found
        exceptionText = getStatementExceptionView("select * from dummypkg.dummy().win:length(10)");
        assertEquals("Failed to resolve event type: Class named 'dummypkg.dummy' could not be loaded and no alias is defined [select * from dummypkg.dummy().win:length(10)]", exceptionText);

        // invalid view
        exceptionText = getStatementExceptionView("select * from " + EVENT_NUM + ".dummy:dummy(10)");
        assertEquals("Error starting view: View name 'dummy' is not a known view name [select * from net.esper.support.bean.SupportBean_N.dummy:dummy(10)]", exceptionText);

        // invalid view parameter
        exceptionText = getStatementExceptionView("select * from " + EVENT_NUM + ".win:length('s')");
        assertEquals("Error starting view: Error invoking constructor for view 'length', the view parameter list is not valid for the view [select * from net.esper.support.bean.SupportBean_N.win:length('s')]", exceptionText);

        // where-clause equals has invalid type compare
        exceptionText = getStatementExceptionView("select * from " + EVENT_NUM + ".win:length(1) where doublePrimitive=true");
        assertEquals("Error validating expression: Implicit conversion from datatype 'java.lang.Double' to 'java.lang.Boolean' is not allowed [select * from net.esper.support.bean.SupportBean_N.win:length(1) where doublePrimitive=true]", exceptionText);

        // where-clause relational op has invalid type
        exceptionText = getStatementExceptionView("select * from " + EVENT_ALLTYPES + ".win:length(1) where string > 5");
        assertEquals("Error validating expression: Implicit conversion from datatype 'java.lang.String' to numeric is not allowed [select * from net.esper.support.bean.SupportBean.win:length(1) where string > 5]", exceptionText);

        // where-clause has aggregation function
        exceptionText = getStatementExceptionView("select * from " + EVENT_ALLTYPES + ".win:length(1) where sum(intPrimitive) > 5");
        assertEquals("Error validating expression: An aggregate function may not appear in a WHERE clause [select * from net.esper.support.bean.SupportBean.win:length(1) where sum(intPrimitive) > 5]", exceptionText);

        // invalid numerical expression
        exceptionText = getStatementExceptionView("select 2 * 's' from " + EVENT_ALLTYPES + ".win:length(1)");
        assertEquals("Error starting view: Implicit conversion from datatype 'java.lang.String' to numeric is not allowed [select 2 * 's' from net.esper.support.bean.SupportBean.win:length(1)]", exceptionText);

        // invalid property in select
        exceptionText = getStatementExceptionView("select a[2].m('a') from " + EVENT_ALLTYPES + ".win:length(1)");
        assertEquals("Error starting view: Property named 'a[2].m('a')' is not valid in any stream [select a[2].m('a') from net.esper.support.bean.SupportBean.win:length(1)]", exceptionText);

        // select clause uses same "as" name twice
        exceptionText = getStatementExceptionView("select 2 as m, 2 as m from " + EVENT_ALLTYPES + ".win:length(1)");
        assertEquals("Error starting view: Property alias name 'm' appears more then once in select clause [select 2 as m, 2 as m from net.esper.support.bean.SupportBean.win:length(1)]", exceptionText);

        // invalid property in group-by
        exceptionText = getStatementExceptionView("select intPrimitive from " + EVENT_ALLTYPES + ".win:length(1) group by xxx");
        assertEquals("Error starting view: Property named 'xxx' is not valid in any stream [select intPrimitive from net.esper.support.bean.SupportBean.win:length(1) group by xxx]", exceptionText);

        // group-by not specifying a property
        exceptionText = getStatementExceptionView("select intPrimitive from " + EVENT_ALLTYPES + ".win:length(1) group by 5");
        assertEquals("Error starting view: Group-by expressions must refer to property names [select intPrimitive from net.esper.support.bean.SupportBean.win:length(1) group by 5]", exceptionText);

        // group-by specifying aggregates
        exceptionText = getStatementExceptionView("select intPrimitive from " + EVENT_ALLTYPES + ".win:length(1) group by sum(intPrimitive)");
        assertEquals("Error starting view: Group-by expressions cannot contain aggregate functions [select intPrimitive from net.esper.support.bean.SupportBean.win:length(1) group by sum(intPrimitive)]", exceptionText);

        // group-by specifying a property that is aggregated through select clause
        exceptionText = getStatementExceptionView("select intPrimitive, sum(doublePrimitive) from " + EVENT_ALLTYPES + ".win:length(1) group by doublePrimitive");
        assertEquals("Error starting view: Group-by property 'doublePrimitive' cannot also occur in an aggregate function in the select clause [select intPrimitive, sum(doublePrimitive) from net.esper.support.bean.SupportBean.win:length(1) group by doublePrimitive]", exceptionText);

        // invalid property in having clause
        exceptionText = getStatementExceptionView("select 2 * 's' from " + EVENT_ALLTYPES + ".win:length(1) group by intPrimitive having xxx > 5");
        assertEquals("Error starting view: Implicit conversion from datatype 'java.lang.String' to numeric is not allowed [select 2 * 's' from net.esper.support.bean.SupportBean.win:length(1) group by intPrimitive having xxx > 5]", exceptionText);

        // invalid having clause - not the same aggregate as used in select
        exceptionText = getStatementExceptionView("select sum(intPrimitive) from " + EVENT_ALLTYPES + ".win:length(1) group by intBoxed having sum(doubleBoxed) > 5");
        assertEquals("Error starting view: Aggregate functions in the HAVING clause must match aggregate functions in the select clause [select sum(intPrimitive) from net.esper.support.bean.SupportBean.win:length(1) group by intBoxed having sum(doubleBoxed) > 5]", exceptionText);

        // invalid having clause - not a symbol in the group-by (non-aggregate)
        exceptionText = getStatementExceptionView("select sum(intPrimitive) from " + EVENT_ALLTYPES + ".win:length(1) group by intBoxed having doubleBoxed > 5");
        assertEquals("Error starting view: Non-aggregated property 'doubleBoxed' in the HAVING clause must occur in the group-by clause [select sum(intPrimitive) from net.esper.support.bean.SupportBean.win:length(1) group by intBoxed having doubleBoxed > 5]", exceptionText);

        // invalid outer join - not a symbol
        exceptionText = getStatementExceptionView("select * from " + EVENT_ALLTYPES + ".win:length(1) as aStr " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) on xxxx=yyyy");
        assertEquals("Error validating expression: Property named 'xxxx' is not valid in any stream [select * from net.esper.support.bean.SupportBean.win:length(1) as aStr left outer join net.esper.support.bean.SupportBean.win:length(1) on xxxx=yyyy]", exceptionText);

        // invalid outer join for 3 streams - not a symbol
        exceptionText = getStatementExceptionView("select * from " + EVENT_ALLTYPES + ".win:length(1) as s0 " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) as s1 on s0.intPrimitive = s1.intPrimitive " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) as s2 on s0.intPrimitive = s2.yyyy");
        assertEquals("Error validating expression: Failed to resolve property 's2.yyyy' to a stream or nested property in a stream [select * from net.esper.support.bean.SupportBean.win:length(1) as s0 left outer join net.esper.support.bean.SupportBean.win:length(1) as s1 on s0.intPrimitive = s1.intPrimitive left outer join net.esper.support.bean.SupportBean.win:length(1) as s2 on s0.intPrimitive = s2.yyyy]", exceptionText);

        // invalid outer join for 3 streams - wrong stream, the properties in on-clause don't refer to streams
        exceptionText = getStatementExceptionView("select * from " + EVENT_ALLTYPES + ".win:length(1) as s0 " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) as s1 on s0.intPrimitive = s1.intPrimitive " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) as s2 on s0.intPrimitive = s1.intPrimitive");
        assertEquals("Error validating expression: Outer join ON-clause must refer to at least one property of the joined stream for stream 2 [select * from net.esper.support.bean.SupportBean.win:length(1) as s0 left outer join net.esper.support.bean.SupportBean.win:length(1) as s1 on s0.intPrimitive = s1.intPrimitive left outer join net.esper.support.bean.SupportBean.win:length(1) as s2 on s0.intPrimitive = s1.intPrimitive]", exceptionText);

        // invalid outer join - referencing next stream
        exceptionText = getStatementExceptionView("select * from " + EVENT_ALLTYPES + ".win:length(1) as s0 " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) as s1 on s2.intPrimitive = s1.intPrimitive " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) as s2 on s1.intPrimitive = s2.intPrimitive");
        assertEquals("Error validating expression: Outer join ON-clause invalid scope for property 'intPrimitive', expecting the current or a prior stream scope [select * from net.esper.support.bean.SupportBean.win:length(1) as s0 left outer join net.esper.support.bean.SupportBean.win:length(1) as s1 on s2.intPrimitive = s1.intPrimitive left outer join net.esper.support.bean.SupportBean.win:length(1) as s2 on s1.intPrimitive = s2.intPrimitive]", exceptionText);

        // invalid outer join - same properties
        exceptionText = getStatementExceptionView("select * from " + EVENT_NUM + ".win:length(1) as aStr " +
                "left outer join " + EVENT_ALLTYPES + ".win:length(1) on string=string");
        assertEquals("Error validating expression: Outer join ON-clause must cannot refer to properties of the same stream [select * from net.esper.support.bean.SupportBean_N.win:length(1) as aStr left outer join net.esper.support.bean.SupportBean.win:length(1) on string=string]", exceptionText);
    }

    public void testInvalidView()
    {
        String eventClass = SupportBean.class.getName();

        tryInvalid("select * from " + eventClass + "(dummy='a').win:length(3)");
        tryValid("select * from " + eventClass + "(string='a').win:length(3)");
        tryInvalid("select * from " + eventClass + ".dummy:length(3)");

        tryInvalid("select djdjdj from " + eventClass + ".win:length(3)");
        tryValid("select boolBoxed as xx, intPrimitive from " + eventClass + ".win:length(3)");
        tryInvalid("select boolBoxed as xx, intPrimitive as xx from " + eventClass + ".win:length(3)");
        tryValid("select boolBoxed as xx, intPrimitive as yy from " + eventClass + "().win:length(3)");

        tryValid("select boolBoxed as xx, intPrimitive as yy from " + eventClass + "().win:length(3)" +
                " where boolBoxed = true");
        tryInvalid("select boolBoxed as xx, intPrimitive as yy from " + eventClass + "().win:length(3)" +
                " where xx = true");
    }

    private void tryInvalid(String viewStmt)
    {
        try
        {
            epService.getEPAdministrator().createEQL(viewStmt);
            fail();
        }
        catch (ASTFilterSpecValidationException ex)
        {
            // expected
        }
        catch (EPException ex)
        {
            // Expected exception
        }
    }

    private String getSyntaxExceptionView(String expression)
    {
        String exceptionText = null;
        try
        {
            epService.getEPAdministrator().createEQL(expression);
            fail();
        }
        catch (EPStatementSyntaxException ex)
        {
            exceptionText = ex.getMessage();
            log.debug(".getSyntaxExceptionView expression=" + expression, ex);
            // Expected exception
        }

        return exceptionText;
    }

    private String getStatementExceptionView(String expression) throws Exception
    {
        return getStatementExceptionView(expression, false);
    }

    private String getStatementExceptionView(String expression, boolean isLogException) throws Exception
    {
        String exceptionText = null;
        try
        {
            epService.getEPAdministrator().createEQL(expression);
            fail();
        }
        catch (EPStatementSyntaxException es)
        {
            throw es;
        }
        catch (EPStatementException ex)
        {
            // Expected exception
            exceptionText = ex.getMessage();
            if (isLogException)
            {
                log.debug(".getStatementExceptionView expression=" + expression, ex);
            }
        }

        return exceptionText;
    }

    private void tryValid(String viewStmt)
    {
        epService.getEPAdministrator().createEQL(viewStmt);
    }

    private static Log log = LogFactory.getLog(TestInvalidView.class);
}
