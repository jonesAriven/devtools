#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 Jenkins Script Console 安装所需插件（修复版）
使用正确的 Jenkins 2.555 API
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
    """通过 Script Console 安装插件 - 使用正确 API"""
    
    # 修复版 Groovy 脚本：使用 Jenkins 2.555 正确的 API
    groovy_script = '''
import jenkins.model.*
import hudson.model.*
import java.util.concurrent.*

def plugins = %s
def installed = []
def failed = []

// 获取 UpdateCenter
def uc = jenkins.model.Jenkins.instance.updateCenter

// 先更新所有站点（异步，需要等待）
println "Refreshing update center..."
uc.updateAllSites()

// 等待更新完成
Thread.sleep(10000)

plugins.each { pluginId ->
    def existingPlugin = jenkins.model.Jenkins.instance.getPlugin(pluginId)
    if (existingPlugin != null) {
        installed << "${pluginId} (already installed v${existingPlugin.version})"
    } else {
        try {
            // 从 update center 获取插件
            def pluginObj = uc.getPlugin(pluginId)
            if (pluginObj == null) {
                failed << "${pluginId} (not found in update center)"
                return
            }
            
            if (!pluginObj.isInstalled()) {
                println "Installing ${pluginId} (v${pluginObj.version})..."
                
                // 安装插件（不重启）
                def installation = pluginObj.deploy(true).get()
                
                Thread.sleep(2000)
                
                // 检查状态
                if (installation.isSuccess()) {
                    installed << "${pluginId} (installed v${pluginObj.version})"
                    println "  -> SUCCESS"
                } else {
                    def status = installation.getStatus()
                    failed << "${pluginId} (status: ${status})"
                    println "  -> FAILED: ${status}"
                }
            } else {
                installed << "${pluginId} (already present, may need restart)"
            }
        } catch (TimeoutException e) {
            failed << "${pluginId} (timeout during install)"
        } catch (Exception e) {
            failed << "${pluginId} (error: ${e.message})"
            e.printStackTrace()
        }
    }
}

println "\\n=== Installation Results ==="
println "SUCCESS (${installed.size()}):"
installed.each { println "  + ${it}" }
println ""
println "FAILED (${failed.size()}):"
failed.each { println "  - ${it}" }

if (installed.any { !it.contains('already') }) {
    println "\\n[INFO] Restart Jenkins to activate new plugins."
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
        resp = opener.open(req, timeout=600)  # 10 分钟超时
        result = resp.read().decode(errors='replace')
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


if __name__ == "__main__":
    import urllib.parse
    
    print("=" * 60)
    print("  Jenkins Plugin Installer v2")
    print(f"  Target: {JENKINS_URL}")
    print("=" * 60)
    
    print("\n[1] Authenticating with Jenkins...")
    opener, crumb, crumb_field, creds = get_crumb_and_session()
    if not crumb:
        sys.exit(1)
    
    success = install_plugins_script_console(opener, crumb, crumb_field, creds)
    
    if success:
        print("\n[DONE]")
    else:
        print("\n[FAILED]")
        sys.exit(1)
