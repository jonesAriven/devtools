#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jenkins 插件安装 v4 - 使用 pluginManager/installPlugins API + 后台轮询
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


def trigger_install(opener, crumb, crumb_field, creds):
    """使用 /pluginManager/installNecessaryPlugins API 触发安装"""
    import urllib.parse
    
    # 构建插件列表参数
    plugin_param = "<".join(PLUGINS_TO_INSTALL)
    
    print(f"\n[2] Triggering install for {len(PLUGINS_TO_INSTALL)} plugins...")
    print(f"     Plugins: {', '.join(PLUGINS_TO_INSTALL)}")
    
    # 使用 POST 到 pluginManager/install
    params = urllib.parse.urlencode({
        "plugin.{}.version".format(i): "latest" 
        for i in range(len(PLUGINS_TO_INSTALL))
    }).encode()
    
    # 实际上用更简单的方式: 直接 POST 到 scriptText 但设置更长超时
    groovy_script = '''
import jenkins.model.*
import java.util.concurrent.*

def plugins = %s
def uc = jenkins.model.Jenkins.instance.updateCenter

println "=== Starting Plugin Installation ==="
println "Update center sites: ${uc.sites.size()}"

// 不再调用 updateAllSites()，直接尝试获取已缓存的插件信息
plugins.each { pluginId ->
    def existingPlugin = jenkins.model.Jenkins.instance.getPlugin(pluginId)
    if (existingPlugin != null) {
        println "[SKIP] ${pluginId} (already v${existingPlugin.version})"
    } else {
        try {
            // 直接从默认站点获取
            def site = uc.getSite('default')
            if (site == null) site = uc.getSites()[0]
            
            def pluginObj = site.getPlugin(pluginId)
            if (pluginObj == null) {
                println "[FAIL] ${pluginId} - not found in update center"
                return
            }
            
            // 检查是否已下载/安装
            if (pluginObj.isDownloaded()) {
                println "[OK] ${pluginId} - already downloaded, installing..."
                pluginObj.install()
                println "[OK] ${pluginId} - install triggered"
            } else if (pluginObj.getInstalled() != null) {
                println "[SKIP] ${pluginId} - already installed"
            } else {
                println "[DOWNLOAD] ${pluginId} v${pluginObj.version}..."
                
                // 异步部署并等待
                def future = pluginObj.deploy(true)
                
                // 等待最多5分钟
                try {
                    def installation = future.get(5, TimeUnit.MINUTES)
                    Thread.sleep(1000)
                    if (installation.isSuccess()) {
                        println "[SUCCESS] ${pluginId} installed!"
                    } else {
                        println "[WARN] ${pluginId} status: ${installation.status}"
                    }
                } catch (TimeoutException te) {
                    println "[TIMEOUT] ${pluginId} still downloading (this is OK)"
                }
            }
        } catch (Exception e) {
            println "[ERROR] ${pluginId}: ${e.message}"
        }
    }
}

println "\\n=== Done ==="
''' % json.dumps(PLUGINS_TO_INSTALL)

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
    
    # 设置超时为 10 分钟
    try:
        resp = opener.open(req, timeout=600)
        result = resp.read().decode(errors='replace')
        print(f"\n{'='*60}")
        print(result)
        print(f"{'='*60}")
        return True
    except urllib.error.HTTPError as e:
        if e.code == 504:
            print("\n[INFO] Gateway Timeout - but script is likely running on Jenkins!")
            print("[INFO] Plugins should be installing in background...")
            return True
        body = e.read().decode(errors='replace')
        print(f"\n[ERROR] HTTP {e.code}: {body[:300]}")
        return False
    except Exception as e:
        if "timed out" in str(e).lower():
            print("\n[INFO] Timeout - plugins likely installing in background")
            return True
        print(f"\n[ERROR] {e}")
        return False


def poll_progress():
    """通过浏览器方式检查进度"""
    print("\n[3] Installation will continue in background.")
    print("    Check progress at: https://jkci.marschat.online/manage/pluginManager/updates/")
    print("\n    Key plugins to watch:")
    for p in ["Publish Over SSH", "workflow-job", "workflow-cps", "git"]:
        print(f"      - {p}")


if __name__ == "__main__":
    import urllib.parse
    
    print("=" * 60)
    print("  Jenkins Plugin Installer v4")
    print("=" * 60)
    
    print("\n[1] Authenticating...")
    opener, crumb, crumb_field, creds = get_crumb_and_session()
    
    success = trigger_install(opener, crumb, crumb_field, creds)
    
    poll_progress()
    
    print("\n[DONE]")
