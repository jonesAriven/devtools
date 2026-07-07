#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jenkins CI/CD - 使用 http.cookiejar 保持 Session + 正确的 Crumb 处理
关键: Jenkins CSRF 需要同一 session 中的 crumb
"""
import sys, json, urllib.request, urllib.error, urllib.parse, base64, ssl, time
from http.cookiejar import CookieJar

sys.stdout.reconfigure(encoding='utf-8', errors='ignore')

JENKINS = "https://jkci.marschat.online"
USER = "admin"
PASS = "admin@Jenkins2024!"
creds = base64.b64encode(f"{USER}:{PASS}".encode()).decode()

# Cookie jar to maintain session
cj = CookieJar()
opener = urllib.request.build_opener(
    urllib.request.HTTPCookieProcessor(cj),
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())
)


def api(method, path, data=None, content_type=None):
    """Jenkins API with session cookies (crumb fetched in same session)"""
    url = f"{JENKINS}{path}"
    h = {"Authorization": f"Basic {creds}"}
    
    body = None
    if data is not None:
        if isinstance(data, dict):
            body = urllib.parse.urlencode(data).encode()
            h["Content-Type"] = "application/x-www-form-urlencoded"
        elif isinstance(data, str):
            body = data.encode('utf-8')
        if content_type:
            h["Content-Type"] = content_type
    
    r = urllib.request.Request(url, method=method, data=body, headers=h)
    try:
        resp = opener.open(r, timeout=30)
        return resp.status, resp.read().decode(errors='replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors='replace')
    except Exception as ex:
        return -1, str(ex)


def get_crumb():
    """Get crumb in the SAME session (cookies are shared)"""
    s, r = api("GET", "/crumbIssuer/api/json")
    if s == 200:
        ci = json.loads(r)
        field = ci.get("crumbRequestField", ".crumb")
        value = ci.get("crumb", "")
        print(f"  Crumb: {field}={value[:35]}...")
        return field, value
    print(f"  WARN get_crumb: {s} {r[:100]}")
    return None, ""


def main():
    print("=" * 60)
    print(f"  Jenkins: {JENKINS}")
    print("=" * 60)

    # Step 1: Get crumb (this sets session cookies)
    print("\n[1] Get CSRF Crumb (with session)...")
    field, crumb_val = get_crumb()

    # Step 2: Create job with crumb header
    print("\n[2] Check/Create 'devtools' job...")
    
    # First check if exists
    s_check, r_check = api("GET", "/job/devtools/api/json")
    
    if s_check != 200:
        print(f"  Job doesn't exist ({s_check}), creating...")
        
        config_xml = '''<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job@1400.v7fd111b_82ca4">
  <description>Devtools CI/CD Pipeline</description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.ChoiceParameterDefinition>
          <name>DEPLOY_PROJECT</name>
          <choices class="java.util.Arrays$ArrayList">
            <string>all</string><string>mykng</string><string>active-manager</string>
            <string>kb-ops</string><string>portal</string><string>infra-monitor</string>
          </choices>
          <description>Deploy project</description>
        </hudson.model.ChoiceParameterDefinition>
        <hudson.model.ChoiceParameterDefinition>
          <name>DEPLOY_TARGET</name>
          <choices class="java.util.Arrays$ArrayList">
            <string>production</string><string>dev</string>
          </choices>
          <description>Target env</description>
        </hudson.model.ChoiceParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>GIT_BRANCH</name><defaultValue>dev</defaultValue>
          <description>Git branch</description><trim>false</trim>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition">
    <scm class="hudson.scm.NullSCM"/>
    <scriptPath>Jenkinsfile</scriptPath>
    <lightweight>true</lightweight>
  </definition>
  <triggers/><disabled>false</disabled>
</flow-definition>'''
        
        # Use createItem with crumb as query param OR header
        # Try header approach first
        h_create = {}
        if crumb_val and field:
            h_create[field] = crumb_val
            h_create["Jenkins-Crumb"] = crumb_val
        
        # We need a custom request with extra headers
        url = f"{JENKINS}/createItem?name=devtools"
        req_h = {"Authorization": f"Basic {creds}", "Content-Type": "application/xml"}
        req_h.update(h_create)
        req = urllib.request.Request(url, data=config_xml.encode('utf-8'), 
                                     headers=req_h, method="POST")
        try:
            resp = opener.open(req, timeout=30)
            print(f"  [OK] Job created! HTTP {resp.status}")
        except urllib.error.HTTPError as e:
            body = e.read().decode(errors='replace')
            if e.code == 400 and "exists" in body.lower():
                print(f"  [INFO] Already exists")
            else:
                print(f"  [FAIL] HTTP {e.code}: {body.strip()[:300]}")
                # Try without XML content-type
                print("  Retrying without explicit CT...")
                req_h2 = {"Authorization": f"Basic {creds}"}
                req_h2.update(h_create)
                req2 = urllib.request.Request(url, data=config_xml.encode('utf-8'),
                                              headers=req_h2, method="POST")
                try:
                    resp2 = opener.open(req2, timeout=30)
                    print(f"  [OK] Retry success! HTTP {resp2.status}")
                except urllib.error.HTTPError as e2:
                    print(f"  [FAIL] HTTP {e2.code}: {e2.read().decode()[:200]}")
    else:
        print("  Job 'devtools' already exists")

    # Step 3: Verify
    print("\n[3] Verify jobs...")
    s_v, r_v = api("GET", "/api/json?tree=jobs[name,url]")
    if s_v == 200:
        for j in json.loads(r_v).get("jobs", []):
            print(f"    {j['name']}")

    # Step 4: Trigger build
    print("\n[4] Trigger build...")
    
    # Re-get crumb (same session)
    f2, c2 = get_crumb()
    
    params_json = json.dumps({
        "parameters": [
            {"name": "DEPLOY_PROJECT", "value": "mykng"},
            {"name": "DEPLOY_TARGET", "value": "dev"},
            {"name": "GIT_BRANCH", "value": "dev"},
        ]
    })
    
    url_build = f"{JENKINS}/job/devtools/buildWithParameters"
    bh = {"Authorization": f"Basic {creds}", "Content-Type": "application/json"}
    if c2:
        bh[f2] = c2
        bh["Jenkins-Crumb"] = c2
    
    breq = urllib.request.Request(url_build, data=params_json.encode(), headers=bh, method="POST")
    try:
        bresp = opener.open(breq, timeout=30)
        print(f"  [OK] Build triggered! HTTP {bresp.status}")
    except urllib.error.HTTPError as be:
        bbody = be.read().decode(errors='replace')
        if be.code in [201, 302]:
            print(f"  [OK] Build triggered! HTTP {be.code}")
        elif be.code == 404:
            print(f"  [WARN] Job not found via API. Use web UI.")
        elif be.code == 403:
            print(f"  [CSRF FAIL] {bbody.strip()[:250]}")
            
            # Last resort: use GET /build (no params)
            print("  Trying simple /build endpoint...")
            url_simple = f"{JENKINS}/job/devtools/build"
            sh = {"Authorization": f"Basic {creds}"}
            if c2:
                sh[f2] = c2
                sh["Jenkins-Crumb"] = c2
            sreq = urllib.request.Request(url_simple, headers=sh, method="POST")
            try:
                sresp = opener.open(sreq, timeout=15)
                print(f"  [OK] Simple build triggered! HTTP {sresp.status}")
            except urllib.error.HTTPError as se:
                print(f"  [FAIL] HTTP {se.code}: {se.read().decode()[:200]}")
        else:
            print(f"  HTTP {be.code}: {bbody.strip()[:250]}")

    # Step 5: Check result after delay
    time.sleep(3)
    print("\n[5] Build status...")
    s_b, r_b = api("GET", "/job/devtools/api/json?tree=lastBuild[number,url,building,result],inQueue")
    if s_b == 200:
        info = json.loads(r_b)
        lb = info.get("lastBuild") or {}
        bn = lb.get("number", "?")
        bu = lb.get("url", "")
        print(f"  Build #{bn}: {bu}")
        print(f"  Console: {JENKINS}/job/devtools/{bn}/consoleFull")
        if info.get("inQueue"):
            print("  Status: QUEUED")
        elif lb.get("building"):
            print("  Status: BUILDING...")
        else:
            print(f"  Result: {lb.get('result', 'N/A')}")

    print("\n[DONE]")


if __name__ == "__main__":
    main()
