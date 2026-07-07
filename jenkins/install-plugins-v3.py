#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 Jenkins Script Console 安装所需插件（v3 - 修复 API 调用）
"""
import urllib.request
import base64
import json
import sys
from http.cookiejar import CookieJar

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

JENKINS_URL = "https://jkci.marschat.online"
USER = "admin"
PASS = "admin123"

PLUGINS_TO_INSTALL = [
    "publish-over-ssh",
    "workflow-job",
    "workflow-cps", 
    "workflow-basic-steps",
    "workflow-durable-task-step",
    "workflow-scm-step",
    "git",
    "credentials-binding",
    "timestamper",
]

def get_crumb_and_session():
    cj = CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    creds = base64.b64encode(f"{USER}:{PASS}".encode()).decode()
    
    req = urllib.request.Request(
        f"{JENKINS_URL}/crumbIssuer/api/json",
        headers={"Authorization": f"Basic {creds}"}
    )
    resp = opener.open(req, timeout=15)
    data = json.loads(resp.read().decode())
    crumb = data.get("crumb")
    crumb_field = data.get("crumbRequestField", "Jenkins-Crumb")
    return opener, crumb, crumb_field, creds


def main():
    import urllib.parse
    
    print("=" * 60)
    print("  Jenkins Plugin Installer v3")
    print("=" * 60)
    
    print("\n[1] Authenticating...")
    opener, crumb, crumb_field, creds = get_crumb_and_session()
    
    # 修复版 Groovy - 使用正确的 API
    groovy_script = '''
import jenkins.model.*
import java.util.concurrent.*

def plugins = %s
def installed = []
def failed = []

def uc = jenkins.model.Jenkins.instance.updateCenter

println "Refreshing update center..."
uc.updateAllSites()
Thread.sleep(10000)

plugins.each { pluginId ->
    def existingPlugin = jenkins.model.Jenkins.instance.getPlugin(pluginId)
    if (existingPlugin != null) {
        installed << "${pluginId} (already v${existingPlugin.version})"
    } else {
        try {
            def pluginObj = uc.getPlugin(pluginId)
            if (pluginObj == null) {
                failed << "${pluginId} (not found)"
                return
            }
            
            // 使用正确的 API: getInstalled() 检查是否已安装
            if (pluginObj.getInstalled() == null) {
                println "Installing ${pluginId}..."
                
                // 异步安装并等待完成
                def future = pluginObj.deploy(true)
                def installation = future.get(10, TimeUnit.MINUTES)
                
                Thread.sleep(1000)
                
                if (installation.isSuccess()) {
                    installed << "${pluginId} (OK)"
                    println "  -> SUCCESS"
                } else {
                    failed << "${pluginId} (fail: ${installation.status})"
                    println "  -> FAILED"
                }
            } else {
                installed << "${pluginId} (already present)"
            }
        } catch (TimeoutException e) {
            failed << "${pluginId} (timeout)"
        } catch (Exception e) {
            failed << "${pluginId} (${e.message})"
        }
    }
}

println "\\n=== Results ==="
println "OK (${installed.size()}):"
installed.each { println "  + ${it}" }
println ""
println "FAIL (${failed.size()}):"
failed.each { println "  - ${it}" }
''' % json.dumps(PLUGINS_TO_INSTALL)

    print("\n[2] Installing plugins via Script Console...")
    
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
        resp = opener.open(req, timeout=600)
        result = resp.read().decode(errors='replace')
        print(f"\n{'='*60}")
        print(result)
        print(f"{'='*60}")
    except Exception as e:
        print(f"\n[ERROR] {e}")
        
    print("\n[DONE]")


if __name__ == "__main__":
    main()
