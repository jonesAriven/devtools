#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jenkins 插件安装 v6 - 使用 pluginManager/installNecessaryPlugins REST endpoint
避免 Groovy 变量名冲突
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

# 需要安装的插件（不含版本，让 Jenkins 自动选最新）
PLUGIN_NAMES = [
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
    print("  Jenkins Plugin Installer v6 (Fixed variable names)")
    print("=" * 60)
    
    print("\n[1] Authenticating...")
    opener, crumb, crumb_field, creds = get_crumb_and_session()
    
    # 修复：使用不同的变量名避免与 Jenkins 内部冲突
    # 关键修改: plugins -> pluginList, site -> updateSite 等
    groovy_script = '''
import jenkins.model.*
import java.util.concurrent.*

// 使用不同的变量名！
def pluginList = %s
def jInstance = jenkins.model.Jenkins.getInstance()
def uc = jInstance.getUpdateCenter()

println "=== Plugin Installation v6 ==="
println "Jenkins version: ${jInstance.version}"
println "Plugins to install: ${pluginList.size()}"

// 获取默认站点
def updateSite = null
uc.getSites().each { s ->
    println "Found site: ${s.url}"
    if (updateSite == null) updateSite = s
}

if (updateSite == null) {
    println "[ERROR] No update site found!"
    return
}

pluginList.each { pId ->
    def existing = jInstance.getPlugin(pId)
    if (existing != null) {
        println "[SKIP] ${pId} (already v${existing.version})"
        return
    }
    
    try {
        def pObj = updateSite.getPlugin(pId)
        if (pObj == null) {
            println "[NOT FOUND] ${pId}"
            return
        }
        
        println "[INSTALL] ${pId} v${pObj.version}..."
        
        // 直接 deploy，不检查状态
        def future = pObj.deploy(true)
        
        try {
            def installResult = future.get(2, TimeUnit.MINUTES)
            Thread.sleep(500)
            if (installResult.isSuccess()) {
                println "[OK] ${pId}"
            } else {
                println "[WARN] ${pId}: ${installResult.status}"
            }
        } catch (TimeoutException te) {
            println "[PENDING] ${pId} downloading..."
        }
    } catch (Exception e) {
        println "[ERROR] ${pId}: ${e.message}"
    }
}

println "\\n=== Done ==="
''' % json.dumps(PLUGIN_NAMES)

    print(f"\n[2] Installing {len(PLUGIN_NAMES)} plugins...")
    
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
        # 设置 10 分钟超时
        resp = opener.open(req, timeout=600)
        result = resp.read().decode(errors='replace')
        print(f"\n{'='*60}")
        print(result)
        print(f"{'='*60}")
    except urllib.error.HTTPError as e:
        if e.code in [504, 502]:
            print(f"\n[INFO] HTTP {e.code} - installation running in background!")
        else:
            body = e.read().decode(errors='replace')
            print(f"\n[ERROR] HTTP {e.code}: {body[:500]}")
    except Exception as e:
        err_str = str(e).lower()
        if "timed out" in err_str or "timeout" in err_str:
            print("\n[INFO] Timeout - plugins installing in background on Jenkins")
        else:
            print(f"\n[ERROR] {e}")
    
    print("\n[DONE]")


if __name__ == "__main__":
    main()
