(function(global, factory) {
  typeof exports === "object" && typeof module !== "undefined" ? factory(exports) : typeof define === "function" && define.amd ? define(["exports"], factory) : (global = typeof globalThis !== "undefined" ? globalThis : global || self, factory(global.MarschatAuth = {}));
})(this, function(exports2) {
  "use strict";var __defProp = Object.defineProperty;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);

  function generateVerifier() {
    const bytes = new Uint8Array(32);
    if (crypto && crypto.getRandomValues) {
      crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < bytes.length; i++) {
        bytes[i] = Math.floor(Math.random() * 256);
      }
    }
    return base64UrlEncode(bytes);
  }
  async function generateChallenge(verifier) {
    if (crypto && crypto.subtle && crypto.subtle.digest) {
      const digest = await crypto.subtle.digest(
        "SHA-256",
        new TextEncoder().encode(verifier)
      );
      return base64UrlEncode(new Uint8Array(digest));
    }
    return base64UrlEncode(sha256Bytes(new TextEncoder().encode(verifier)));
  }
  function base64UrlEncode(bytes) {
    let bin = "";
    for (let i = 0; i < bytes.length; i++) {
      bin += String.fromCharCode(bytes[i]);
    }
    return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }
  function generateState() {
    const bytes = new Uint8Array(16);
    if (crypto && crypto.getRandomValues) {
      crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < bytes.length; i++) {
        bytes[i] = Math.floor(Math.random() * 256);
      }
    }
    return base64UrlEncode(bytes);
  }
  function sha256Bytes(msg) {
    const K = new Uint32Array([
      1116352408,
      1899447441,
      3049323471,
      3921009573,
      961987163,
      1508970993,
      2453635748,
      2870763221,
      3624381080,
      310598401,
      607225278,
      1426881987,
      1925078388,
      2162078206,
      2614888103,
      3248222580,
      3835390401,
      4022224774,
      264347078,
      604807628,
      770255983,
      1249150122,
      1555081692,
      1996064986,
      2554220882,
      2821834349,
      2952996808,
      3210313671,
      3336571891,
      3584528711,
      113926993,
      338241895,
      666307205,
      773529912,
      1294757372,
      1396182291,
      1695183700,
      1986661051,
      2177026350,
      2456956037,
      2730485921,
      2820302411,
      3259730800,
      3345764771,
      3516065817,
      3600352804,
      4094571909,
      275423344,
      430227734,
      506948616,
      659060556,
      883997877,
      958139571,
      1322822218,
      1537002063,
      1747873779,
      1955562222,
      2024104815,
      2227730452,
      2361852424,
      2428436474,
      2756734187,
      3204031479,
      3329325298
    ]);
    let h0 = 1779033703, h1 = 3144134277, h2 = 1013904242, h3 = 2773480762;
    let h4 = 1359893119, h5 = 2600822924, h6 = 528734635, h7 = 1541459225;
    const msgLen = msg.length;
    const blockLen = Math.ceil((msgLen + 9) / 64) * 64;
    const m = new Uint8Array(blockLen);
    m.set(msg);
    m[msgLen] = 128;
    const bitLen = msgLen * 8;
    m[blockLen - 4] = bitLen >>> 24 & 255;
    m[blockLen - 3] = bitLen >>> 16 & 255;
    m[blockLen - 2] = bitLen >>> 8 & 255;
    m[blockLen - 1] = bitLen & 255;
    const w = new Uint32Array(64);
    for (let off = 0; off < blockLen; off += 64) {
      for (let t = 0; t < 16; t++) {
        const j = off + t * 4;
        w[t] = m[j] << 24 | m[j + 1] << 16 | m[j + 2] << 8 | m[j + 3];
      }
      for (let t = 16; t < 64; t++) {
        const s0 = ror(w[t - 15], 7) ^ ror(w[t - 15], 18) ^ w[t - 15] >>> 3;
        const s1 = ror(w[t - 2], 17) ^ ror(w[t - 2], 19) ^ w[t - 2] >>> 10;
        w[t] = w[t - 16] + s0 + w[t - 7] + s1 | 0;
      }
      let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, hh = h7;
      for (let tt = 0; tt < 64; tt++) {
        const S1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25);
        const ch = e & f ^ ~e & g;
        const temp1 = hh + S1 + ch + K[tt] + w[tt] | 0;
        const S0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22);
        const maj = a & b ^ a & c ^ b & c;
        const temp2 = S0 + maj | 0;
        hh = g;
        g = f;
        f = e;
        e = d + temp1 | 0;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2 | 0;
      }
      h0 = h0 + a | 0;
      h1 = h1 + b | 0;
      h2 = h2 + c | 0;
      h3 = h3 + d | 0;
      h4 = h4 + e | 0;
      h5 = h5 + f | 0;
      h6 = h6 + g | 0;
      h7 = h7 + hh | 0;
    }
    const out = new Uint8Array(32);
    const hs = [h0, h1, h2, h3, h4, h5, h6, h7];
    for (let i = 0; i < 8; i++) {
      out[i * 4] = hs[i] >>> 24 & 255;
      out[i * 4 + 1] = hs[i] >>> 16 & 255;
      out[i * 4 + 2] = hs[i] >>> 8 & 255;
      out[i * 4 + 3] = hs[i] & 255;
    }
    return out;
  }
  function ror(x, n) {
    return x >>> n | x << 32 - n;
  }
  const DEFAULT_CONFIG = {
    accessTokenKey: "auth_access_token",
    refreshTokenKey: "auth_refresh_token",
    tokenKindKey: "auth_token_kind",
    /**
     * id_token 存储键。
     *
     * ★ 为什么必须存：OIDC RP-Initiated Logout 要求 `id_token_hint`，
     *   而 SAS 的 /connect/logout 不给 hint 直接 400（实测 2026-09-11）。
     *   授权码换票时返回的 id_token 必须留住，登出时原样带回去，
     *   IdP 才能定位到要销毁哪个会话。
     */
    idTokenKey: "auth_id_token"
  };
  const COOKIE_CONFIG = {
    /**
     * ★ 是否允许**前端 JS 写 Cookie**。默认 `false`（2026-09-11 起）。
     *
     * 为什么默认关：`sso_access_token` / `sso_refresh_token` 是 **auth-center 后端写的 HttpOnly Cookie**
     * （Domain=marschat.online）。前端若也往 `Domain=.marschat.online` 写同名 Cookie，浏览器会
     * **同时存在两份同名 Cookie**，后端读取时可能拿到前端那份（非 HttpOnly、可被 XSS 偷），
     * 且两份的过期时间/内容可能不一致 —— 属于"自己给自己埋雷"。
     *
     * 因此默认只写 localStorage；确有"非 SSO 模式跨域调试"需求时，由应用显式 `initTokenConfig({ cookie: { enabled: true } })` 打开。
     */
    enabled: false,
    /** SSO Access Token Cookie 名称（与后端一致） */
    accessTokenName: "sso_access_token",
    /** SSO Refresh Token Cookie 名称 */
    refreshTokenName: "sso_refresh_token",
    /** Cookie 域名 - 覆盖所有子域 */
    domain: ".marschat.online",
    /** Cookie 路径 */
    path: "/",
    /** 是否使用安全连接 */
    secure: true,
    /** SameSite 策略 */
    sameSite: "Lax",
    /** 过期时间（秒） */
    maxAge: 7200
    // 2 小时
  };
  let config = { ...DEFAULT_CONFIG };
  function initTokenConfig(options = {}) {
    if (options == null ? void 0 : options.cookie) {
      Object.assign(COOKIE_CONFIG, options.cookie);
    }
    config = { ...DEFAULT_CONFIG, ...options };
  }
  function setCookie(name, value, options) {
    const opts = { ...COOKIE_CONFIG, ...options };
    let cookieStr = `${name}=${encodeURIComponent(value)}`;
    cookieStr += `; Domain=${opts.domain}`;
    cookieStr += `; Path=${opts.path}`;
    if (opts.secure) cookieStr += "; Secure";
    cookieStr += `; SameSite=${opts.sameSite}`;
    cookieStr += `; Max-Age=${opts.maxAge}`;
    document.cookie = cookieStr;
  }
  function getCookie(name) {
    if (!document.cookie || !document.cookie.includes(name)) return null;
    const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
    return match ? decodeURIComponent(match[1]) : null;
  }
  function deleteCookie(name) {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=${COOKIE_CONFIG.domain}`;
  }
  function getToken() {
    const cookieVal = getCookie(COOKIE_CONFIG.accessTokenName);
    if (cookieVal) return cookieVal;
    return localStorage.getItem(config.accessTokenKey);
  }
  function setToken(token) {
    localStorage.setItem(config.accessTokenKey, token);
    if (!COOKIE_CONFIG.enabled) return;
    try {
      setCookie(COOKIE_CONFIG.accessTokenName, token);
    } catch (e) {
      console.warn("[auth] Failed to set token cookie:", e);
    }
  }
  function removeToken() {
    if (COOKIE_CONFIG.enabled) {
      deleteCookie(COOKIE_CONFIG.accessTokenName);
    }
    localStorage.removeItem(config.accessTokenKey);
  }
  function setRefreshToken(token) {
    localStorage.setItem(config.refreshTokenKey, token);
    if (!COOKIE_CONFIG.enabled) return;
    try {
      setCookie(COOKIE_CONFIG.refreshTokenName, token);
    } catch (e) {
      console.warn("[auth] Failed to set refresh token cookie:", e);
    }
  }
  function removeRefreshToken() {
    if (COOKIE_CONFIG.enabled) {
      deleteCookie(COOKIE_CONFIG.refreshTokenName);
    }
    localStorage.removeItem(config.refreshTokenKey);
  }
  function getIdToken() {
    try {
      return localStorage.getItem(config.idTokenKey);
    } catch {
      return null;
    }
  }
  function setIdToken(token) {
    if (!token) return;
    try {
      localStorage.setItem(config.idTokenKey, token);
    } catch (e) {
      console.warn("[auth] Failed to persist id_token:", e);
    }
  }
  function removeIdToken() {
    try {
      localStorage.removeItem(config.idTokenKey);
    } catch {
    }
  }
  function clearTokens() {
    removeToken();
    removeRefreshToken();
    removeIdToken();
    removeTokenKind();
  }
  function getTokenKind() {
    return localStorage.getItem(config.tokenKindKey) || "legacy";
  }
  function setTokenKind(kind) {
    localStorage.setItem(config.tokenKindKey, kind);
  }
  function removeTokenKind() {
    localStorage.removeItem(config.tokenKindKey);
  }
  function isOidcToken() {
    return getTokenKind() === "oidc";
  }
  function decodeOidcClaims(token) {
    try {
      const payload = token.split(".")[1];
      const json = new TextDecoder().decode(
        Uint8Array.from(atob(payload.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0))
      );
      return JSON.parse(json);
    } catch {
      return {};
    }
  }
  const SESSION_KEYS = {
    state: "auth_sso_state",
    verifier: "auth_sso_verifier",
    redirect: "auth_sso_redirect"
  };
  async function startSsoLogin(config2, redirect = "/dashboard") {
    const state = generateState();
    const verifier = generateVerifier();
    const challenge = await generateChallenge(verifier);
    sessionStorage.setItem(SESSION_KEYS.state, state);
    sessionStorage.setItem(SESSION_KEYS.verifier, verifier);
    sessionStorage.setItem(SESSION_KEYS.redirect, redirect);
    const params = new URLSearchParams({
      client_id: config2.clientId,
      redirect_uri: config2.redirectUri,
      response_type: "code",
      scope: config2.scope || "openid profile",
      state,
      code_challenge: challenge,
      code_challenge_method: "S256"
    });
    window.location.assign(`${config2.issuer}/oauth2/authorize?${params.toString()}`);
    return new Promise(() => {
    });
  }
  async function handleSsoCallback(config2, searchParams = new URLSearchParams(window.location.search)) {
    const code = searchParams.get("code");
    const state = searchParams.get("state");
    const error = searchParams.get("error");
    if (error) {
      throw new Error(`统一认证拒绝: ${searchParams.get("error_description") || error}`);
    }
    if (!code) {
      throw new Error("授权回调缺少 code");
    }
    const savedState = sessionStorage.getItem(SESSION_KEYS.state);
    const verifier = sessionStorage.getItem(SESSION_KEYS.verifier);
    if (!savedState || savedState !== state) {
      throw new Error("state 校验失败，请重新发起登录");
    }
    if (!verifier) {
      throw new Error("PKCE 凭据丢失，请重新发起登录");
    }
    const body = new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: config2.redirectUri,
      client_id: config2.clientId,
      code_verifier: verifier
    });
    const res = await fetch(`${config2.issuer}/oauth2/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body
    });
    if (!res.ok) {
      const detail = await res.text().catch(() => "");
      throw new Error(`换取令牌失败(HTTP ${res.status}): ${detail.slice(0, 120)}`);
    }
    const data = await res.json();
    sessionStorage.removeItem(SESSION_KEYS.state);
    sessionStorage.removeItem(SESSION_KEYS.verifier);
    const target = sessionStorage.getItem(SESSION_KEYS.redirect) || "/dashboard";
    sessionStorage.removeItem(SESSION_KEYS.redirect);
    setTokenKind("oidc");
    setToken(data.access_token);
    if (data.refresh_token) {
      setRefreshToken(data.refresh_token);
    }
    if (data.id_token) {
      setIdToken(data.id_token);
    }
    return target;
  }
  function buildSsoAuthorizeUrl(config2) {
    const params = new URLSearchParams({
      client_id: config2.clientId,
      redirect_uri: config2.redirectUri,
      response_type: "code",
      scope: config2.scope || "openid profile"
    });
    return `${config2.issuer}/oauth2/authorize?${params.toString()}`;
  }
  function authCenterBase(config2) {
    return (config2.authCenterBase || config2.issuer || "").replace(/\/+$/, "");
  }
  function normalizeRedirect(redirect) {
    const v = (redirect || "").trim();
    if (!v) return "/dashboard";
    return v.startsWith("/") ? v : `/${v}`;
  }
  async function probeIdpSession(config2, options = {}) {
    const timeoutMs = options.timeoutMs ?? 4e3;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(`${authCenterBase(config2)}/auth/session`, {
        method: "GET",
        credentials: "include",
        headers: { Accept: "application/json" },
        signal: controller.signal
      });
      if (!res.ok) return { authenticated: false, ok: false };
      const body = await res.json().catch(() => null);
      const payload = body && typeof body === "object" && body.data ? body.data : body;
      return {
        authenticated: !!(payload == null ? void 0 : payload.authenticated),
        username: (payload == null ? void 0 : payload.username) ?? null,
        // 只有解析出对象才算「探针成功执行」，此时 authenticated 才可用于主动登出判定
        ok: payload != null
      };
    } catch {
      return { authenticated: false, ok: false };
    } finally {
      clearTimeout(timer);
    }
  }
  async function silentSignIn(config2, redirect) {
    const probe = await probeIdpSession(config2);
    if (!probe.authenticated) {
      return false;
    }
    await startSsoLogin(config2, normalizeRedirect(redirect));
    return true;
  }
  function buildSloUrl(config2, options = {}) {
    const params = new URLSearchParams();
    const hint = options.idTokenHint ?? getIdToken();
    if (hint) params.set("id_token_hint", hint);
    const back = options.postLogoutRedirectUri ?? config2.loginUrl;
    if (back) params.set("post_logout_redirect_uri", back);
    if (options.state) params.set("state", options.state);
    const qs = params.toString();
    return `${authCenterBase(config2)}/auth/slo${qs ? `?${qs}` : ""}`;
  }
  function ssoLogout(config2, options = {}) {
    const hint = options.idTokenHint ?? getIdToken() ?? void 0;
    const url = buildSloUrl(config2, { ...options, idTokenHint: hint });
    clearLocalAuth();
    window.location.assign(url);
  }
  async function renewByReauthorize(config2, redirect) {
    const current = `${window.location.pathname}${window.location.search}`;
    let target = redirect || current;
    if (!redirect) {
      try {
        const cbPath = new URL(config2.redirectUri, window.location.origin).pathname;
        const base = cbPath.replace(/\/[^/]*$/, "");
        if (base && current.startsWith(base)) {
          target = current.slice(base.length) || "/";
        }
      } catch {
      }
    }
    clearTokens();
    await startSsoLogin(config2, target);
    return new Promise(() => {
    });
  }
  async function bootstrapLoginPage(config2, redirect, options = {}) {
    if (!options.force && config2.silentLogin === false) {
      return false;
    }
    return silentSignIn(config2, redirect);
  }
  function clearLocalAuth() {
    clearTokens();
    try {
      localStorage.removeItem("auth_user");
    } catch {
    }
  }
  const DEFAULTS = {
    intervalMs: 6e4,
    watchVisibility: true,
    watchFocus: true,
    watchStorage: true,
    timeoutMs: 4e3,
    confirmCount: 1,
    redirectOnLost: true
  };
  function isOnLoginPage(loginUrl) {
    try {
      const cur = new URL(window.location.href);
      const target = new URL(loginUrl, window.location.origin);
      return cur.origin === target.origin && cur.pathname === target.pathname;
    } catch {
      return false;
    }
  }
  function createSessionWatcher(config2, options = {}) {
    const opts = { ...DEFAULTS, ...options };
    const loginUrl = options.loginUrl || config2.loginUrl || "/login";
    const readToken = options.getToken ?? getToken;
    const clearAuth = options.clearLocalAuth ?? clearLocalAuthSafely;
    let running = false;
    let paused = false;
    let timer = null;
    let probing = false;
    let lostStreak = 0;
    let firstProbeTimer = null;
    async function tick() {
      var _a, _b, _c;
      if (!running || paused || probing) return true;
      if (!readToken()) {
        return false;
      }
      probing = true;
      try {
        const probe = await probeIdpSession(config2, { timeoutMs: opts.timeoutMs });
        if (!probe.ok) {
          lostStreak = 0;
          (_a = opts.onProbeError) == null ? void 0 : _a.call(opts, new Error("session probe failed (untrusted)"));
          return true;
        }
        if (probe.authenticated) {
          lostStreak = 0;
          const localId = (_b = opts.getLocalIdentity) == null ? void 0 : _b.call(opts);
          if (localId && probe.username && String(probe.username) !== String(localId)) {
            stopWatcher();
            clearAuth();
            if (opts.onIdentityMismatch) {
              opts.onIdentityMismatch();
            } else {
              if (opts.redirectOnLost && !isOnLoginPage(loginUrl)) {
                const sep = loginUrl.includes("?") ? "&" : "?";
                window.location.assign(`${loginUrl}${sep}reauth=1`);
              }
            }
            return false;
          }
          return true;
        }
        lostStreak += 1;
        if (lostStreak < opts.confirmCount) {
          return true;
        }
        lostStreak = 0;
        const username = probe.username ?? null;
        stopWatcher();
        clearAuth();
        (_c = opts.onSessionLost) == null ? void 0 : _c.call(opts, { username, reason: "probe" });
        if (opts.redirectOnLost && !isOnLoginPage(loginUrl)) {
          const sep = loginUrl.includes("?") ? "&" : "?";
          window.location.assign(`${loginUrl}${sep}slo=1`);
        }
        return false;
      } finally {
        probing = false;
      }
    }
    function clearLocalAuthSafely() {
      try {
        clearTokens();
      } catch {
      }
      try {
        localStorage.removeItem("auth_user");
      } catch {
      }
    }
    function stopWatcher() {
      running = false;
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
      if (firstProbeTimer) {
        clearTimeout(firstProbeTimer);
        firstProbeTimer = null;
      }
      document.removeEventListener("visibilitychange", onWakeup);
      window.removeEventListener("focus", onWakeup);
      window.removeEventListener("storage", onStorage);
    }
    function onWakeup() {
      if (!running || paused) return;
      if (document.visibilityState === "hidden") return;
      void tick();
    }
    function onStorage() {
      if (!running || paused) return;
      void tick();
    }
    return {
      start() {
        if (running) return;
        running = true;
        paused = false;
        if (opts.watchVisibility) document.addEventListener("visibilitychange", onWakeup);
        if (opts.watchFocus) window.addEventListener("focus", onWakeup);
        if (opts.watchStorage) window.addEventListener("storage", onStorage);
        if (opts.intervalMs > 0) {
          timer = setInterval(() => void tick(), opts.intervalMs);
        }
        firstProbeTimer = setTimeout(() => void tick(), 3e3);
      },
      stop: stopWatcher,
      pause() {
        paused = true;
      },
      resume() {
        paused = false;
      },
      probeNow() {
        return tick();
      },
      isRunning() {
        return running;
      },
      isPaused() {
        return paused;
      }
    };
  }
  function startSessionWatcher(config2, options = {}) {
    const watcher = createSessionWatcher(config2, options);
    watcher.start();
    return watcher;
  }
  function createSsoClient(config2) {
    if (!(config2 == null ? void 0 : config2.issuer)) {
      throw new Error("[marschat-auth] createSsoClient 缺少 issuer");
    }
    if (!(config2 == null ? void 0 : config2.clientId)) {
      throw new Error("[marschat-auth] createSsoClient 缺少 clientId");
    }
    if (!(config2 == null ? void 0 : config2.redirectUri)) {
      throw new Error("[marschat-auth] createSsoClient 缺少 redirectUri");
    }
    return {
      /** 当前配置（只读快照） */
      config: { ...config2 },
      /** 会话探针：auth-center 侧是否还有有效 IdP 会话 */
      probeSession: (opts) => probeIdpSession(config2, opts),
      /** 静默免登：有会话直接跳授权（不返回），无会话返回 false */
      silentSignIn: (redirect) => silentSignIn(config2, redirect),
      /** 登录页入口编排：有会话免登，无会话返回 false（正常显示登录框） */
      bootstrapLoginPage: (redirect, opts) => bootstrapLoginPage(config2, redirect, opts),
      /** 主动发起登录（跳授权端点，不返回） */
      login: (redirect) => startSsoLogin(config2, redirect),
      /** 处理授权回调：用 code + PKCE 换票，返回落地路径 */
      handleCallback: (searchParams) => handleSsoCallback(config2, searchParams),
      /** 静默续期：重跑一次授权（token 过期 / 收到 401 时调用，不返回） */
      renew: (redirect) => renewByReauthorize(config2, redirect || `${window.location.pathname}${window.location.search}`),
      /** 统一登出（SLO）：销毁 IdP 会话 + 清本地，然后回跳 */
      logout: (options) => ssoLogout(config2, options),
      /** 仅构建登出 URL（需要自己控制跳转时机时用） */
      buildLogoutUrl: (options) => buildSloUrl(config2, options),
      /** 构建授权 URL（自绘 SSO 按钮时用） */
      buildAuthorizeUrl: () => buildSsoAuthorizeUrl(config2),
      /** 仅清本地凭据，不碰 IdP 会话 */
      clearLocalAuth: () => clearLocalAuth(),
      /**
       * 启动**会话监视**（Phase 6：单点登出跨应用联动）。
       *
       * SAS 不支持 back-channel logout，其他应用在别处登出后本地 token 不会失效。
       * 监视线程会在「页面切回可见 / 窗口获焦 / 定时（默认 60s）」时探一次
       * `/auth/session`，**确认**会话消失才清本地并跳登录页；探针失败一律保持现状。
       *
       * @returns 监视器句柄 —— **应用主动登出前必须 `.stop()`**，否则登出跳转途中
       *          监视器可能再判定一次，造成多余的二次跳转。
       *
       * @example
       * ```ts
       * const watcher = sso.watchSession({ intervalMs: 60_000 })
       * // 退出登录时：
       * watcher.stop()
       * sso.logout()
       * ```
       */
      watchSession: (options) => startSessionWatcher(config2, options),
      // ---- token 便捷方法 ----
      getToken: () => getToken(),
      getIdToken: () => getIdToken(),
      decodeClaims: (token) => decodeOidcClaims(token || getToken() || ""),
      isOidc: () => isOidcToken()
    };
  }
  class UserAdminError extends Error {
    constructor(message, status = 0) {
      super(message);
      /** HTTP 状态码（0 表示未拿到响应，如网络异常） */
      __publicField(this, "status");
      this.name = "UserAdminError";
      this.status = status;
    }
  }
  function createUserAdminClient(options) {
    const base = (options.baseUrl || "").replace(/\/+$/, "");
    if (!base) {
      throw new UserAdminError("[marschat-auth] createUserAdminClient 缺少 baseUrl");
    }
    const doFetch = options.fetchImpl ?? fetch;
    const timeoutMs = options.timeoutMs ?? 15e3;
    async function request(path, init) {
      const url = new URL(`${base}${path}`, window.location.origin);
      if (init.query) {
        for (const [k, v] of Object.entries(init.query)) {
          if (v !== void 0 && v !== null && v !== "") url.searchParams.set(k, String(v));
        }
      }
      const headers = { Accept: "application/json" };
      if (init.body !== void 0) headers["Content-Type"] = "application/json";
      const token = options.getToken ? options.getToken() : null;
      if (token) headers["Authorization"] = `Bearer ${token}`;
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), timeoutMs);
      let res;
      try {
        res = await doFetch(url.toString(), {
          method: init.method,
          headers,
          body: init.body === void 0 ? void 0 : JSON.stringify(init.body),
          signal: controller.signal
        });
      } catch (e) {
        clearTimeout(timer);
        throw new UserAdminError(
          e instanceof Error && e.name === "AbortError" ? "请求超时，请稍后重试" : "网络异常，请检查连接",
          0
        );
      } finally {
        clearTimeout(timer);
      }
      if (res.status === 401 || res.status === 403) {
        if (res.status === 401 && options.onUnauthorized) {
          try {
            options.onUnauthorized();
          } catch {
          }
        }
        throw new UserAdminError(
          res.status === 401 ? "登录已过期，请重新登录" : "当前账号无权限管理用户",
          res.status
        );
      }
      if (res.status === 204) return void 0;
      const text = await res.text().catch(() => "");
      let payload = null;
      if (text) {
        try {
          payload = JSON.parse(text);
        } catch {
          payload = null;
        }
      }
      if (!res.ok) {
        throw new UserAdminError((payload == null ? void 0 : payload.message) || `请求失败(HTTP ${res.status})`, res.status);
      }
      if (payload && typeof payload === "object" && "code" in payload) {
        if (payload.code !== 200) {
          throw new UserAdminError(payload.message || `操作失败(code ${payload.code})`, res.status);
        }
        return payload.data;
      }
      return payload;
    }
    return {
      list(query = {}) {
        return request("", {
          method: "GET",
          query: {
            realmId: query.realmId,
            keyword: query.keyword,
            page: query.page ?? 1,
            size: query.size ?? 20
          }
        }).then((r) => ({
          list: (r == null ? void 0 : r.list) ?? [],
          total: (r == null ? void 0 : r.total) ?? 0,
          page: (r == null ? void 0 : r.page) ?? query.page ?? 1,
          size: (r == null ? void 0 : r.size) ?? query.size ?? 20
        }));
      },
      create(payload) {
        return request("", { method: "POST", body: payload });
      },
      update(id, payload) {
        return request(`/${id}`, { method: "PUT", body: payload });
      },
      remove(id) {
        return request(`/${id}`, { method: "DELETE" });
      },
      resetPassword(id, newPassword) {
        return request(`/${id}/password`, { method: "PUT", body: { newPassword } });
      }
    };
  }
  const version = "0.5.4";
  exports2.bootstrapLoginPage = bootstrapLoginPage;
  exports2.buildSloUrl = buildSloUrl;
  exports2.buildSsoAuthorizeUrl = buildSsoAuthorizeUrl;
  exports2.clearLocalAuth = clearLocalAuth;
  exports2.clearTokens = clearTokens;
  exports2.createSessionWatcher = createSessionWatcher;
  exports2.createSsoClient = createSsoClient;
  exports2.createUserAdminClient = createUserAdminClient;
  exports2.decodeOidcClaims = decodeOidcClaims;
  exports2.getIdToken = getIdToken;
  exports2.getToken = getToken;
  exports2.getTokenKind = getTokenKind;
  exports2.handleSsoCallback = handleSsoCallback;
  exports2.initTokenConfig = initTokenConfig;
  exports2.isOidcToken = isOidcToken;
  exports2.probeIdpSession = probeIdpSession;
  exports2.renewByReauthorize = renewByReauthorize;
  exports2.setIdToken = setIdToken;
  exports2.setToken = setToken;
  exports2.setTokenKind = setTokenKind;
  exports2.silentSignIn = silentSignIn;
  exports2.ssoLogout = ssoLogout;
  exports2.startSessionWatcher = startSessionWatcher;
  exports2.startSsoLogin = startSsoLogin;
  exports2.version = version;
  Object.defineProperty(exports2, Symbol.toStringTag, { value: "Module" });
});
