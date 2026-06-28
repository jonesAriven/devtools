package com.kb.knowledge;

import io.cucumber.core.options.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Cucumber BDD 运行配置类（SOP V1.1 阶段2.5 强制要求）
 * <p>
 * 扫描 src/test/resources/features/ 下的 .feature 文件（doc_lifecycle.feature、share_access.feature），
 * glue 包路径为 com.kb.knowledge，Step Definitions 需放在该包下。
 * 命名为 *IT 以纳入 failsafe 集成测试阶段执行。
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "com.kb.knowledge")
public class CucumberIT {
}
