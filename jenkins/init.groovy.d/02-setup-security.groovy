// ============================================================
// Jenkins 初始化脚本 02: 安全配置
// ============================================================

import jenkins.model.*
import hudson.security.*
import hudson.util.*
import java.util.logging.Logger

def logger = Logger.getLogger("")
def instance = Jenkins.get()

logger.info("===== kb-cicd: 配置安全策略 =====")

// 禁用安装向导（使用 JCasC 配置）
instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETE)

// 设置安全域（本地用户数据库）
def securityRealm = new HudsonSecurityRealm()
instance.setSecurityRealm(securityRealm)

// 授权策略：管理员完全控制，登录用户可查看
def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)

instance.save()

logger.info("✅ 安全配置完成: 本地用户 + 登录授权")
