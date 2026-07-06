// ============================================================
// Jenkins 初始化脚本 01: 安装插件
// ============================================================
// 首次启动时自动安装 plugins.txt 中列出的插件
// ============================================================

import jenkins.model.*
import hudson.InstallUtil
import java.util.logging.Logger

def logger = Logger.getLogger("")
logger.info("===== kb-cicd: 开始安装预配置插件 =====")

def instance = Jenkins.get()
def pluginManager = instance.getPluginManager()
def updateCenter = instance.getUpdateCenter()

// 读取插件列表
def pluginsFile = new File("/usr/share/jenkins/ref/plugins.txt")
if (!pluginsFile.exists()) {
    logger.warning("plugins.txt 不存在，跳过自动安装")
    return
}

def plugins = []
pluginsFile.eachLine { line ->
    line = line.trim()
    if (line && !line.startsWith("#")) {
        def parts = line.split(":")
        if (parts.length >= 1) {
            plugins.add([name: parts[0], version: parts.length > 1 ? parts[1] : null])
        }
    }
}

logger.info("发现 ${plugins.size()} 个待安装插件")

// 安装插件
def installed = 0
plugins.each { plugin ->
    def pluginName = plugin.name
    def version = plugin.version
    
    // 检查是否已安装
    if (pluginManager.getPlugin(pluginName)) {
        logger.info("✅ ${pluginName} 已安装，跳过")
        return
    }
    
    try {
        def ucPlugin = updateCenter.getPlugin(pluginName, version)
        if (ucPlugin != null) {
            logger.info("⬇️ 正在安装 ${pluginName}${version ? '@' + version : ''} ...")
            ucPlugin.deploy(true)
            installed++
            logger.info("✅ ${pluginName} 安装成功")
        } else {
            logger.warning("❌ ${pluginName}${version ? '@' + version : ''} 在更新中心未找到")
        }
    } catch (Exception e) {
        logger.severe("❌ ${pluginName} 安装失败: ${e.message}")
    }
}

logger.info("===== kb-cicd: 插件安装完成，新安装 ${installed} 个 =====")

// 等待所有插件就绪（重要！）
if (installed > 0) {
    logger.info("等待插件初始化...")
    InstallUtil.saveLastFailedPlugins(instance)
}
