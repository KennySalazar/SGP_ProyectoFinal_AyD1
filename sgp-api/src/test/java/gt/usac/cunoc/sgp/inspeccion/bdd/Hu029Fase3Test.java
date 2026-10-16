package gt.usac.cunoc.sgp.inspeccion.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/hu029/fase3")
@ConfigurationParameter(key = "cucumber.glue", value = "gt.usac.cunoc.sgp.inspeccion.bdd")
@ConfigurationParameter(key = "cucumber.junit-platform.naming-strategy", value = "long")
@ConfigurationParameter(
    key = "cucumber.plugin",
    value = "pretty,json:target/cucumber/hu029-fase3.json,html:target/cucumber/hu029-fase3.html")
public class Hu029Fase3Test {}
