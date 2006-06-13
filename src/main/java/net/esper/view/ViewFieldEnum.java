package net.esper.view;

import java.util.Arrays;

/**
 * Enumerates the valid values for each view's public fields. The name of the field or property can be used
 * to obtain values from the view rather than using the hardcoded String value for the field.
 */
public enum ViewFieldEnum
{
    /**
     * Count.
     */
    UNIVARIATE_STATISTICS__COUNT ("count"),

    /**
     * Sum.
     */
    UNIVARIATE_STATISTICS__SUM ("sum"),

    /**
     * Average.
     */
    UNIVARIATE_STATISTICS__AVERAGE ("average"),

    /**
     * Standard dev population.
     */
    UNIVARIATE_STATISTICS__STDDEVPA ("stddevpa"),

    /**
     * Standard dev.
     */
    UNIVARIATE_STATISTICS__STDDEV ("stddev"),

    /**
     * Variance.
     */
    UNIVARIATE_STATISTICS__VARIANCE ("variance"),

    /**
     * Weighted average.
     */
    WEIGHTED_AVERAGE__AVERAGE ("average"),

    /**
     * Correlation.
     */
    CORRELATION__CORRELATION ("correlation"),

    /**
     * Slope.
     */
    REGRESSION__SLOPE("slope"),

    /**
     * Y-intercept.
     */
    REGRESSION__YINTERCEPT("YIntercept"),

    /**
     * Size.
     */
    SIZE_VIEW__SIZE ("size"),

    /**
     * Cube.
     */
    MULTIDIM_OLAP__CUBE ("cube");

    /**
     * Measures in an OLAP cube.
     */
    public static final String[] MULTIDIM_OLAP__MEASURES = {
            ViewFieldEnum.UNIVARIATE_STATISTICS__COUNT.getName(),
            ViewFieldEnum.UNIVARIATE_STATISTICS__SUM.getName(),
            ViewFieldEnum.UNIVARIATE_STATISTICS__AVERAGE.getName(),
            ViewFieldEnum.UNIVARIATE_STATISTICS__STDDEVPA.getName(),
            ViewFieldEnum.UNIVARIATE_STATISTICS__STDDEV.getName(),
            ViewFieldEnum.UNIVARIATE_STATISTICS__VARIANCE.getName()
        };

    static
    {
        Arrays.sort(MULTIDIM_OLAP__MEASURES);
    }

    private final String name;

    ViewFieldEnum(String name)
    {
        this.name = name;
    }

    /**
     * Returns the field name of fields that contain data within a view's posted objects.
     * @return field name for use with DataSchema to obtain values out of objects.
     */
    public String getName()
    {
        return name;
    }
}
