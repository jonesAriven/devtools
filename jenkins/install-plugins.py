#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 Jenkins Script Console 安装所需插件
"""
import urllib.request
import urllib.error
import base64
import json
import time
import sys
from http.cookiejar import CookieJar

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

JENKINS_URL = "https://jkci.marschat.online"
USER = "admin"
PASS = "admin123"

# 需要安装的插件列表
PLUGINS_TO_INSTALL = [
    "publish-over-ssh",   # SSH 发布插件（核心！）
    "workflow-job",       # Pipeline 任务类型（核心！）
    "workflow-cps",       # Pipeline Groovy 运行时
    "workflow-basic-steps",  # Pipeline 基础步骤 (sh, echo 等)
    "workflow-durable-task-step",  # Pipeline sh 步骤实现
    "workflow-scm-step",   # Pipeline checkout 步骤
    "git",                 # Git SCM
    "credentials-binding", # 凭据绑定
    "timestamper",         # 构建时间戳
]

def get_crumb_and_session():
    """获取 Crumb 和 Session"""
    cj = CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    
    creds = base64.b64encode(f"{USER}:{PASS}".encode()).decode()
    
    # 获取 Crumb
    req = urllib.request.Request(
        f"{JENKINS_URL}/crumbIssuer/api/json",
        headers={"Authorization": f"Basic {creds}"}
    )
    
    try:
        resp = opener.open(req, timeout=15)
        data = json.loads(resp.read().decode())
        crumb = data.get("crumb")
        crumb_field = data.get("crumbRequestField", "Jenkins-Crumb")
        print(f"  [OK] Got crumb: {crumb[:20]}...")
        return opener, crumb, crumb_field, creds
    except Exception as e:
        print(f"  [FAIL] Get crumb: {e}")
        return None, None, None, None


def install_plugins_script_console(opener, crumb, crumb_field, creds):
    """通过 Script Console 安装插件"""
    
    # Groovy 脚本：使用 Jenkins 内置的插件安装 API
    groovy_script = '''
import jenkins.model.*
import hudson.model.*
import java.util.concurrent.*

def plugins = %s
def pm = Jenkins.instance.extensionList[jenkins.model.JenkinsUpdateSite][0]
def installed = []
def failed = []

plugins.each { pluginId ->
    def plugin = jenkins.model.Jenkins.instance.getPlugin(pluginId)
    if (plugin != null) {
        installed << "${pluginId} (already installed v${plugin.version})"
    } else {
        // 尝试从 update center 安装
        try {
            def uc = jenkins.model.Jenkins.instance.updateCenter
            uc.updateAllSites()
            
            // 等待更新中心数据加载
            Thread.sleep(5000)
            
            def pluginObj = uc.getPlugin(pluginId)
            if (pluginObj == null) {
                failed << "${pluginId} (not found in update center)"
                return
            }
            
            if (!pluginObj.isInstalled()) {
                println "Installing ${pluginId}..."
                def future = pluginObj.deploy(true)
                def installation = future.get(5, TimeUnit.MINUTES)
                
                // 检查是否需要重启
                if (installation.isSuccess()) {
                    installed << "${pluginId} (installed successfully)"
                    if (installation.isRestartRequiredForCompletion()) {
                        println "  -> Restart required for ${pluginId}"
                    }
                } else {
                    failed << "${pluginId} (installation failed: ${installation.status})"
                }
            } else {
                installed << "${pluginId} (already present)"
            }
        } catch (Exception e) {
            failed << "${pluginId} (error: ${e.message})"
        }
    }
}

println "\\n=== Installation Results ==="
println "SUCCESS (${installed.size()}):"
installed.each { println "  + ${it}" }
println ""
println "FAILED (${failed.size()}):"
failed.each { println "  - ${it}" }

// 如果有新安装的插件，标记需要重启
if (installed.any { !it.contains('already') }) {
    println "\\n[INFO] A restart may be required to activate new plugins."
}
''' % json.dumps(PLUGINS_TO_INSTALL)

    print("\n[2] Sending install script to Jenkins Script Console...")
    
    params = urllib.parse.urlencode({"script": groovy_script}).encode()
    req = urllib.request.Request(
        f"{JENKINS_URL}/scriptText",
        data=params,
        headers={
            "Authorization": f"Basic {creds}",
            crumb_field: crumb,
            "Content-Type": "application/x-www-form-urlencoded",
        },
        method="POST"
    )
    
    try:
        resp = opener.open(req, timeout=300)  # 5 分钟超时，安装可能很慢
        result = resp.read().decode(errors='replace')
        print(f"\n  [OK] Script executed!")
        print(f"\n{'='*60}")
        print(result)
        print(f"{'='*60}")
        return True
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors='replace')
        print(f"\n  [ERROR] HTTP {e.code}: {body[:500]}")
        return False
    except Exception as e:
        print(f"\n  [ERROR] {e}")
        return False


def check_installation_status(opener, crumb, crumb_field, creds):
    """检查已安装的插件"""
    print("\n[3] Checking installed plugins status...")
    
    req = urllib.request.Request(
        f"{JENKINS_URL}/pluginManager/api/json?depth=1",
        headers={
            "Authorization": f"Basic {creds}",
            crumb_field: crumb,
        }
    )
    
    try:
        resp = opener.open(req, timeout=30)
        data = json.loads(resp.read().decode())
        
        plugins = data.get("plugins", [])
        
        # 检查我们需要的插件
        needed = ["publish-over-ssh", "workflow-job", "workflow-cps", 
                  "workflow-basic-steps", "git", "credentials-binding"]
        
        print(f"\n  Total installed plugins: {len(plugins)}")
        print(f"\n  {'Plugin':<30} {'Version':<20} {'Status'}")
        print(f"  {'-'*30} {'-'*20} {'-'*10}")
        
        for p in plugins:
            short_name = p.get("shortName", "")
            if short_name in needed or any(n in short_name for n in needed):
                version = p.get("version", "?")
                enabled = p.get("enabled", False)
                active = p.get("active", False)
                has_updates = p.get("hasUpdates", False)
                status = "✅ Active" if (enabled and active) else ("⚠️ Enabled" if enabled else "❌ Disabled")
                if has_updates:
                    status += " (update available)"
                print(f"  {short_name:<30} {version:<20} {status}")
        
        return True
    except Exception as e:
        print(f"  [ERROR] Check status: {e}")
        return False


if __name__ == "__main__":
    import urllib.parse
    
    print("=" * 60)
    print("  Jenkins Plugin Installer via Script Console")
    print(f"  Target: {JENKINS_URL}")
    print(f"  Plugins: {', '.join(PLUGINS_TO_INSTALL)}")
    print("=" * 60)
    
    # 1. 获取认证信息
    print("\n[1] Authenticating with Jenkins...")
    opener, crumb, crumb_field, creds = get_crumb_and_session()
    if not crumb:
        sys.exit(1)
    
    # 2. 通过 Script Console 安装插件
    success = install_plugins_script_console(opener, crumb, crumb_field, creds)
    
    if success:
        # 3. 检查安装状态
        time.sleep(2)
        check_installation_status(opener, crumb, crumb_field, creds)
    
    print("\n[DONE]")
