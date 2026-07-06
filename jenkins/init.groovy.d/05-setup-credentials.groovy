// ============================================================
// Jenkins 初始化脚本 05: 凭据配置
// ============================================================
// 从环境变量读取敏感信息，创建 Jenkins 凭据
// 环境变量通过 docker-compose.yml 注入
// ============================================================

import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*
import hudson.util.Secret
import java.util.logging.Logger

def logger = Logger.getLogger("")
def instance = Jenkins.get()
def domain = Domain.global()
def store = instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()

logger.info("===== kb-cicd: 配置凭据 =====")

// 辅助函数：添加用户名密码凭据
def addUsernamePassword(id, username, password, description) {
    if (!password || password.isEmpty()) {
        logger.warning("⚠️ ${id}: 密码为空，跳过")
        return
    }
    
    // 检查是否已存在
    def existing = store.getCredentials(domain).find { it.id == id }
    if (existing) {
        logger.info("⏭️ ${id} 已存在，跳过")
        return
    }
    
    def credential = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        id,
        description,
        username,
        Secret.fromString(password)
    )
    store.addCredentials(domain, credential)
    logger.info("✅ ${id}: 创建成功")
}

// 辅助函数：添加 Secret Text 凭据
def addSecretText(id, secret, description) {
    if (!secret || secret.isEmpty()) {
        logger.warning("⚠️ ${id}: 值为空，跳过")
        return
    }
    
    def existing = store.getCredentials(domain).find { it.id == id }
    if (existing) {
        logger.info("⏭️ ${id} 已存在，跳过")
        return
    }
    
    def credential = new StringCredentialsImpl(
        CredentialsScope.GLOBAL,
        id,
        description,
        Secret.fromString(secret)
    )
    store.addCredentials(domain, credential)
    logger.info("✅ ${id}: 创建成功")
}

// 从环境变量读取并创建凭据
def env = System.getenv()

// SSH 部署密码
addUsernamePassword(
    "ssh-deploy-mykng",
    "root",
    env['DEPLOY_PASS_MYKNG'] ?: '',
    "SSH部署密码 - mykng (100.93.36.113)"
)

addUsernamePassword(
    "ssh-deploy-lan",
    "root",
    env['DEPLOY_PASS_LAN'] ?: '',
    "SSH部署密码 - 内网Debian (192.168.31.182)"
)

// Nexus 私服凭据
addUsernamePassword(
    "nexus-credential",
    env['NEXUS_USER'] ?: 'admin',
    env['NEXUS_PASS'] ?: '',
    "Nexus 私服凭据"
)

// Gitee Token（用于 Webhook 触发）
addSecretText(
    "gitee-token",
    env['GITEE_TOKEN'] ?: '',
    "Gitee API Token"
)

instance.save()
logger.info("===== kb-cicd: 凭据配置完成 =====")
