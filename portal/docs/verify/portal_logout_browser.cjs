/**
 * portal 登出链路 · 真浏览器验收脚本（headless，零依赖，仅用 Node 内置 WebSocket/fetch）
 *
 * 用途：验证「登录 → 用户管理页 → 退出登录」全链路，并断言登出后本地凭据被清空。
 * 配合服务端断言（登出后旧 token 调管理端点应 401）构成 T-ENG-8 的完整验收。
 *
 * 用法（凭证只从环境变量读，**代码内不落任何账号密码**）：
 *   PORTAL_BASE=http://192.168.31.105:8095/portal/ \
 *   PORTAL_USER=admin PORTAL_PASS='***' \
 *   PORTAL_SHOTS=./shots \
 *   node portal_logout_browser.cjs
 *
 * ⚠️ 扩展名必须是 .cjs：portal/package.json 声明了 "type": "module"，.js 会被当作 ESM
 *    而本脚本用 require（无依赖，仅 Node 内置模块）。
 *
 * 环境变量：
 *   PORTAL_BASE   必填，portal 入口（带 /portal/ 前缀，末尾斜杠）
 *   PORTAL_USER   必填
 *   PORTAL_PASS   必填
 *   PORTAL_EDGE   可选，Edge 可执行文件路径（默认两处常见安装路径）
 *   PORTAL_SHOTS  可选，截图输出目录（默认当前目录）
 *   PORTAL_PORT   可选，CDP 端口（默认 9333）
 *
 * ⚠️ 三个必备约束（缺一即误判，详见迁移记录 §8）：
 *   1) headless —— 不占用操作者桌面；
 *   2) --no-proxy-server —— 沙箱/代理环境下不加会全部超时；
 *   3) Network.setCacheDisabled + 视口 1440×900 —— 否则会跑历史 bundle / 落移动端断点（桌面侧边栏 DOM 不挂载）。
 */
const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const EDGE = process.env.PORTAL_EDGE
  || (fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
    ? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
    : 'C:/Program Files/Microsoft/Edge/Application/msedge.exe');
const BASE = process.env.PORTAL_BASE;
const USER = process.env.PORTAL_USER;
const PASS = process.env.PORTAL_PASS;
const SHOTS = process.env.PORTAL_SHOTS || process.cwd();
const PORT = Number(process.env.PORTAL_PORT || 9333);
const PROFILE = path.join(SHOTS, '.edge-profile-logout');

if (!BASE || !USER || !PASS) {
  console.error('缺少必需环境变量：PORTAL_BASE / PORTAL_USER / PORTAL_PASS');
  process.exit(2);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log('[verify]', ...a);
const results = [];
const record = (name, ok, detail) => { results.push({ name, ok, detail }); log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ' — ' + detail : ''}`); };

async function main() {
  fs.mkdirSync(SHOTS, { recursive: true });
  fs.rmSync(PROFILE, { recursive: true, force: true });
  const proc = spawn(EDGE, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${PORT}`,
    '--no-proxy-server', '--no-first-run', '--no-default-browser-check',
    `--user-data-dir=${PROFILE}`, 'about:blank'], { stdio: 'ignore' });

  let target = null;
  for (let i = 0; i < 40; i++) {
    await sleep(500);
    try {
      const arr = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      const p = arr.find((t) => t.type === 'page' && t.webSocketDebuggerUrl);
      if (p) { target = p; break; }
    } catch (e) { /* 未就绪 */ }
  }
  if (!target) { log('无法连接 Edge（检查 PORTAL_EDGE 路径与端口占用）'); proc.kill(); process.exit(1); }

  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let id = 0;
  const pending = new Map();
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  };
  const send = (method, params = {}) => new Promise((res) => { const mid = ++id; pending.set(mid, res); ws.send(JSON.stringify({ id: mid, method, params })); });
  const js = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })).result?.result?.value;
  const shot = async (name) => {
    const r = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(SHOTS, name), Buffer.from(r.result.data, 'base64'));
  };

  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
  await send('Network.setCacheDisabled', { cacheDisabled: true });
  await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });

  // 1. 打开门户（未登录应被重定向到登录页）
  await send('Page.navigate', { url: BASE });
  await sleep(2500);
  const url1 = await js('location.href');
  record('未登录访问 /portal/ 被重定向到登录页', url1.includes('/login'), url1);
  await shot('shot-1-login.png');

  // 2. 账密登录
  const filled = await js(`(() => {
    const list = [...document.querySelectorAll('input')].filter(i => i.type === 'text' || i.type === '');
    const pwd = document.querySelector('input[type="password"]');
    const u = list.find(i => i.type !== 'password');
    if (!u || !pwd) return 'no-inputs';
    const set = (el, v) => {
      const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      s.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    };
    set(u, ${JSON.stringify(USER)}); set(pwd, ${JSON.stringify(PASS)});
    return 'filled';
  })()`);
  record('登录表单可填充', filled === 'filled', filled);
  await sleep(400);
  await shot('shot-2-filled.png');

  await js(`(() => {
    const b = [...document.querySelectorAll('button')].find(x => /登\\s*录|Login|Sign in/.test(x.innerText) && !x.disabled);
    if (b) { b.click(); return 1; } return 0;
  })()`);
  await sleep(3500);
  const afterLogin = await js('JSON.stringify({url: location.href, token: !!localStorage.getItem("portal_token"), role: localStorage.getItem("portal_role"), kind: localStorage.getItem("portal_token_kind")})');
  const al = JSON.parse(afterLogin);
  record('账密登录成功并写入本地会话', !!al.token, `url=${al.url} role=${al.role} kind=${al.kind}`);
  await shot('shot-3-after-login.png');

  // 3. 用户管理页（管理员应能看到真实数据行）
  await send('Page.navigate', { url: BASE + 'users' });
  await sleep(3000);
  const rows = await js(`document.querySelectorAll('.el-table__body-wrapper tbody tr').length`);
  const usersUrl = await js('location.href');
  record('用户管理页进入且渲染数据行', usersUrl.includes('/users') && Number(rows) > 0, `rows=${rows}`);
  await shot('shot-4-users.png');

  // 4. 真实登出路径：头像下拉 → 退出登录 → 确认
  const openMenu = await js(`(() => {
    const dd = document.querySelector('header .el-dropdown, .layout-header .el-dropdown, .el-dropdown');
    if (!dd) return 'no-dropdown';
    dd.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true }));
    dd.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
    dd.click();
    return 'ok';
  })()`);
  await sleep(900);
  await shot('shot-5-menu.png');

  const clickLogout = await js(`(() => {
    const it = [...document.querySelectorAll('.el-dropdown-menu__item')].find(x => /退出登录|登出|Logout/.test(x.innerText));
    if (!it) return 'no-item';
    it.click(); return 'ok';
  })()`);
  await sleep(900);
  await shot('shot-6-confirm.png');

  const confirmed = await js(`(() => {
    const ok = [...document.querySelectorAll('.el-message-box__btns button')].find(b => /确定|OK/.test(b.innerText));
    if (!ok) return 'no-confirm';
    ok.click(); return 'ok';
  })()`);
  record('登出交互可达（菜单 / 退出项 / 确认弹窗）',
    openMenu === 'ok' && clickLogout === 'ok' && confirmed === 'ok',
    `${openMenu}/${clickLogout}/${confirmed}`);

  // 5. 登出后：离开工作台 + 本地凭据清空
  for (let i = 0; i < 8; i++) {
    await sleep(1000);
    const u = await js('location.href');
    if (!u.includes('/users')) break;
  }
  await sleep(1500);
  const afterLogout = await js('location.href');
  await shot('shot-7-after-logout.png');

  await send('Page.navigate', { url: BASE });
  await sleep(2500);
  const ls = await js('JSON.stringify({t:localStorage.getItem("portal_token"),u:localStorage.getItem("portal_user"),r:localStorage.getItem("portal_role"),k:localStorage.getItem("portal_token_kind")})');
  const l = JSON.parse(ls);
  record('登出后本地凭据已清空', !l.t && !l.u && !l.k, ls);
  record('登出后回到未登录态（被重定向登录页）', (await js('location.href')).includes('/login'), afterLogout);
  await shot('shot-8-back.png');

  ws.close(); proc.kill();

  const failed = results.filter((r) => !r.ok);
  log('──────────────────────────────');
  log(`结果：${results.length - failed.length}/${results.length} 通过`);
  if (failed.length) { log('失败项：'); failed.forEach((f) => log(`  - ${f.name} (${f.detail})`)); }
  log('⚠️ 服务端侧仍需配套断言：登出后旧 token 调管理端点应 401');
  process.exit(failed.length ? 1 : 0);
}
main().catch((e) => { console.error('[fatal]', e && e.message); process.exit(1); });
