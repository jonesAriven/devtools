#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jenkins 插件安装 v5 - 使用 pluginManager/installNecessaryPlugins REST API
"""
import urllib.request
import base64
import json
import time
import sys
from http.cookiejar import CookieJar

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

JENKINS_URL = "https://jkci.marschat.online"
USER = "admin"
PASS = "admin123"

PLUGINS_TO_INSTALL = [
    "publish-over-ssh:latest",
    "workflow-job:latest",
    "workflow-cps:latest", 
    "workflow-basic-steps:latest",
    "workflow-durable-task-step:latest",
    "workflow-scm-step:latest",
    "git:latest",
    "credentials-binding:latest",
    "timestamper:latest",
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
    print("  Jenkins Plugin Installer v5 (REST API)")
    print("=" * 60)
    
    print("\n[1] Authenticating...")
    opener, crumb, crumb_field, creds = get_crumb_and_session()
    
    # 方法: 使用 /pluginManager/installPlugins?... 或 /pluginManager/preconfiguredConfig/form
    # 最可靠的方式: 直接 POST 到 pluginManager/installPlugins
    
    print(f"\n[2] Installing {len(PLUGINS_TO_INSTALL)} plugins via REST API...")
    
    # 构建插件参数
    plugin_xml = ""
    for p in PLUGINS_TO_INSTALL:
        name, version = p.rsplit(":", 1) if ":" in p else (p, "latest")
        plugin_xml += f'<jenkins.install.InstallState><id>{name}</id></jenkins.install.InstallState>'
    
    # 实际上用最简单的方式 - 通过 script console 调用 installPlugins 方法
    groovy_script = '''
import jenkins.model.*
import hudson.PluginWrapper
import jenkins.model.Jenkins

def plugins = %s
def j = Jenkins.instance

println "=== Installing Plugins ==="
println "Current installed count: ${j.plugins.size()}"

def uc = j.updateCenter
def site = uc.sites[0]
println "Update site: ${site.url}"

// 简单方式：直接调用 deploy
plugins.each { pluginId ->
    def existing = j.getPlugin(pluginId)
    if (existing != null) {
        println "[SKIP] ${pluginId} (v${existing.version})"
        return
    }
    
    try {
        def p = site.getPlugin(pluginId)
        if (p == null) {
            // 尝试从所有站点查找
            uc.sites.each { s ->
                def found = s.getPlugin(pluginId)
                if (found != null && p == null) p = found
            }
        }
        
        if (p == null) {
            println "[NOT FOUND] ${pluginId}"
            return
        }
        
        println "[INSTALLING] ${pluginId} v${p.version}..."
        
        // 直接调用 deploy，不检查状态
        def future = p.deploy(true)
        
        // 非阻塞等待
        try {
            def result = future.get(1, java.util.concurrent.TimeUnit.MINUTES)
            Thread.sleep(500)
            println "[DONE] ${pluginId} -> ${result.isSuccess() ? 'SUCCESS' : result.status}"
        } catch (java.util.concurrent.TimeoutException te) {
            println "[PENDING] ${pluginId} still downloading..."
        }
    } catch (Exception e) {
        println "[ERROR] ${pluginId}: ${e.getClass().name} - ${e.message}"
    }
}

println "\\n=== Complete ==="
''' % json.dumps([p.split(":")[0] for p in PLUGINS_TO_INSTALL])

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
    except urllib.error.HTTPError as e:
        if e.code in [504, 502]:
            print(f"\n[INFO] HTTP {e.code} Timeout - installation running in background!")
            print("[INFO] This is expected for large plugin downloads.")
        else:
            body = e.read().decode(errors='replace')
            print(f"\n[ERROR] HTTP {e.code}: {body[:300]}")
    except Exception as e:
        if "timed out" in str(e).lower():
            print("\n[INFO] Connection timed out - plugins installing in background!")
        else:
            print(f"\n[ERROR] {e}")
    
    print("\n[DONE] Check progress at: https://jkci.marschat.online/manage/pluginManager/updates/")


if __name__ == "__main__":
    main()
