package net.esper.client;

import junit.framework.TestCase;

import java.net.URL;
import java.io.File;

public class TestConfiguration extends TestCase
{
    protected static final String ESPER_TEST_CONFIG = "regression/esper.test.readconfig.cfg.xml";

    private Configuration config;

    public void setUp()
    {
        config = new Configuration();
    }

    public void testString() throws Exception
    {
        config.configure(ESPER_TEST_CONFIG);
        TestConfigurationParser.assertFileConfig(config);
    }

    public void testURL() throws Exception
    {
        URL url = this.getClass().getClassLoader().getResource(ESPER_TEST_CONFIG);
        config.configure(url);
        TestConfigurationParser.assertFileConfig(config);
    }

    public void testFile() throws Exception
    {
        URL url = this.getClass().getClassLoader().getResource(ESPER_TEST_CONFIG);
        File file = new File(url.toURI());
        config.configure(file);
        TestConfigurationParser.assertFileConfig(config);
    }

    public void testAddEventTypeAlias()
    {
        config.addEventTypeAlias("AEventType", "BClassName");

        assertEquals(1, config.getEventTypeAliases().size());
        assertEquals("BClassName", config.getEventTypeAliases().get("AEventType"));
        assertDefaultConfig();
    }

    private void assertDefaultConfig()
    {
        assertEquals(4, config.getImports().size());
        assertEquals("java.lang.*", config.getImports().get(0));
        assertEquals("java.math.*", config.getImports().get(1));
        assertEquals("java.text.*", config.getImports().get(2));
        assertEquals("java.util.*", config.getImports().get(3));
    }
}
