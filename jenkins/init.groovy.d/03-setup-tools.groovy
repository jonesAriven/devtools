// ============================================================
// Jenkins 初始化脚本 03: 配置构建工具（Maven + JDK）
// ============================================================

import jenkins.model.*
import hudson.tasks.*
import hudson.tools.*

def logger = java.util.logging.Logger.getLogger("")
def instance = Jenkins.get()
def env = System.getInstance()

logger.info("===== kb-cicd: 配置构建工具 =====")

// Maven 配置
def mavenList = instance.getDescriptorByType(Maven.DescriptorImpl.class)
def mavenInstallations = mavenList.getInstallations()

if (mavenInstallations.length == 0) {
    logger.info("配置 Maven 3.9...")
    def maven = new MavenInstallation(
        "Maven 3.9",           // 名称
        null,                  // MAVEN_HOME (null = 自动安装)
        [                      // InstallSource
            new InstallSourceProperty([
                new Maven.MavenInstaller("3.9.9")
            ])
        ]
    )
    mavenList.setInstallations([maven])
    logger.info("✅ Maven 3.9 配置完成")
} else {
    logger.info("Maven 已配置: ${mavenInstallations.collect { it.name }.join(', ')}")
}

// JDK 配置
def jdkList = instance.getDescriptorByType(JDK.DescriptorImpl.class)
def jdkInstallations = jdkList.getInstallations()

if (jdkInstallations.length == 0) {
    logger.info("配置 JDK 21...")
    // Jenkins LTS 镜像已内置 JDK 21，直接使用
    def jdk = new JDK("JDK 21", System.getenv("JAVA_HOME"))
    jdkList.setInstallations([jdk])
    logger.info("✅ JDK 21 配置完成 (内置)")
} else {
    logger.info("JDK 已配置: ${jdkInstallations.collect { it.name }.join(', ')}")
}

instance.save()

logger.info("===== kb-cicd: 构建工具配置完成 =====")
