/*
 * activecode 统一认证 SSO —— 公共组件「薄适配层」
 * ============================================================================
 * 本文件**不再自己实现** OIDC/PKCE/会话探针/SLO，全部委托给公共组件：
 *
 *   @marschat/auth-components@0.5.0  →  dist/marschat-auth-core.umd.js
 *   （框架无关 UMD 单文件，挂 window.MarschatAuth；sha256 见同目录 VENDORED.md）
 *
 * 这里只保留「本应用特有」的两件事：
 *   1. localStorage 键名映射（activecode_* 历史键，保证老用户不掉线）
 *   2. 换票成功后调后端 /activecode/api/auth/sso-login 建立服务端 session
 *
 * 能力（与其余 5 个应用完全一致，同一份实现）：
 *   - 静默免登 bootstrapLoginPage：登录页探测到 IdP 会话直接免密进入
 *   - 会话探针 probeSession：问 auth-center /auth/session（读服务端 Security 上下文）
 *   - 统一登出 logout（SLO）：带 id_token_hint 跳 /auth/slo 销毁 IdP 会话，
 *     而不是只清本地（只清本地会被下一个应用静默免登回登不掉的假象）
 *   - Token 静默续期 renew：public client 无 refresh_token，改走「静默重授权」
 *   - 会话监视 startSessionWatcher：跨应用 SLO 联动（他处登出 → 本应用自动登出）
 * ============================================================================
 */
(function (global) {
    'use strict';

    var MICRO = global.MarschatAuth;
    var ORIGIN = global.location.origin;

    // ---- 本应用常量 ----
    var CLIENT_ID = 'marschat-activecode';
    var REDIRECT_URI = ORIGIN + '/activecode/sso-callback.html';
    var LOGIN_URL = ORIGIN + '/activecode/login.html';
    var DEFAULT_REDIRECT = '/activecode/main.html';
    var ISSUER = 'https://auth.marschat.online';

    /** 用户名落地键（本应用历史键，保持兼容） */
    var USER_KEY = 'activecode_sso_user';

    /** 静默续期防回环护栏：记录上次重授权时间戳 */
    var RENEW_GUARD_KEY = 'activecode_renew_guard';
    var RENEW_GUARD_MS = 10000;

    /**
     * 显式的待清理历史键。
     *
     * ★ 组件侧 `clearLocalAuth()` 只会清「按 initTokenConfig 配置的键」+ `auth_user`，
     *   清不到 activecode 自己的 `activecode_sso_user`。这里显式列出，避免残留。
     */
    var LEGACY_KEYS = [
        'activecode_oidc_access',
        'activecode_oidc_refresh',
        'activecode_oidc_id',
        'activecode_token_kind',
        USER_KEY
    ];

    /** UMD 未加载（CDN/静态资源 404）时的显式失败标记 */
    var LOAD_ERROR = '统一认证组件 marschat-auth-core.umd.js 未加载，请检查静态资源是否部署完整';

    var sso = null;

    /** 会话监视器单例（SLO 联动：IdP 会话失效 → 本应用自动登出/回登录页） */
    var watcher = null;

    if (!MICRO || typeof MICRO.createSsoClient !== 'function') {
        // 不在这里抛错：登录页/回调页会在调用时拿到明确错误并展示给用户。
        if (global.console && console.error) console.error('[activecode] ' + LOAD_ERROR);
    } else {
        // 1) token 键名映射：沿用 activecode_* 历史键，老用户无需重新登录
        MICRO.initTokenConfig({
            accessTokenKey: 'activecode_oidc_access',
            refreshTokenKey: 'activecode_oidc_refresh',
            idTokenKey: 'activecode_oidc_id',
            tokenKindKey: 'activecode_token_kind'
        });

        // 2) 绑配置的 SSO 客户端
        sso = MICRO.createSsoClient({
            issuer: ISSUER,
            clientId: CLIENT_ID,
            redirectUri: REDIRECT_URI,
            loginUrl: LOGIN_URL,
            silentLogin: true
        });
    }

    function client() {
        if (!sso) throw new Error(LOAD_ERROR);
        return sso;
    }

    /** 清掉本应用全部 SSO 前端态（组件清配置键，这里补清应用自有键） */
    function clearLocalState() {
        try {
            client().clearLocalAuth();
        } catch (e) {
            /* 组件不可用时也要尽量清干净 */
        }
        try {
            for (var i = 0; i < LEGACY_KEYS.length; i++) {
                localStorage.removeItem(LEGACY_KEYS[i]);
            }
        } catch (e) {
            /* ignore */
        }
    }

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    /**
     * 发起统一认证登录（跳 auth-center 授权端点，不返回）。
     * @param {string} [redirect] 换票成功后的落地路径，默认 /activecode/main.html
     */
    function startSsoLogin(redirect) {
        return client().login(redirect || DEFAULT_REDIRECT);
    }

    /**
     * 登录页入口编排：有 IdP 会话 → 静默免登（不返回）；无会话 → resolve(false)。
     * @returns {Promise<boolean>}
     */
    function bootstrapLoginPage(redirect) {
        return client().bootstrapLoginPage(redirect || DEFAULT_REDIRECT);
    }

    /** 会话探针（auth-center 侧是否还有有效 IdP 会话） */
    function probeSession(opts) {
        return client().probeSession(opts);
    }

    /** 仅静默免登（不做其他编排） */
    function silentSignIn(redirect) {
        return client().silentSignIn(redirect || DEFAULT_REDIRECT);
    }

    // ------------------------------------------------------------------
    // 回调（sso-callback.html）
    // ------------------------------------------------------------------

    /** 从 id_token 里取登录主体标识（auth-center 签发 sub=用户ID，id_token 无 preferred_username） */
    function usernameFromToken() {
        var claims = {};
        try {
            claims = MICRO.decodeOidcClaims(MICRO.getIdToken() || '') || {};
        } catch (e) {
            claims = {};
        }
        // 顺序与原实现保持一致：后端 /sso-login 用 access_token 的 sub 做一致性校验，
        // 而 id_token 与 access_token 的 sub 同源（都是 principal name），故取 sub 最稳。
        return claims.preferred_username || claims.unique_name || claims.sub || claims.name || '';
    }

    /**
     * 处理授权回调：换票 → 建立后端 session → 返回落地路径。
     *
     * 与原实现的行为差异：token 落地由组件统一处理（含 id_token 留存供 SLO 用），
     * 本函数只补「调后端 /sso-login」这一步（后端要对 RS256 access_token 验签）。
     *
     * @returns {Promise<{redirect: string, username: string}>}
     */
    function handleSsoCallback() {
        return client().handleCallback().then(function (redirect) {
            var username = usernameFromToken();
            // 直接用 localStorage 读，避免 getToken() 的 Cookie 优先策略干扰
            var accessToken = '';
            try {
                accessToken = localStorage.getItem('activecode_oidc_access') || '';
            } catch (e) {
                accessToken = '';
            }
            if (username) {
                try {
                    localStorage.setItem(USER_KEY, username);
                } catch (e) {
                    /* ignore */
                }
            }

            // 后端需对 RS256 access_token 验签，必须一并发过去
            return fetch('/activecode/api/auth/sso-login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: username, access_token: accessToken })
            }).then(function (r) {
                if (!r.ok) {
                    return r.json().catch(function () { return {}; }).then(function (loginData) {
                        throw new Error('SSO 登录后端失败: ' + (loginData.message || ('HTTP ' + r.status)));
                    });
                }
                return r.json().catch(function () { return {}; });
            }).then(function (loginData) {
                if (!loginData.success) {
                    throw new Error('SSO 登录后端失败: ' + (loginData.message || '未知错误'));
                }
                return {
                    redirect: redirect || DEFAULT_REDIRECT,
                    username: username || loginData.username || ''
                };
            });
        });
    }

    // ------------------------------------------------------------------
    // 会话监视（SLO 跨应用联动）
    // ------------------------------------------------------------------

    /**
     * 启动会话监视器：定时/切标签页/获得焦点/跨标签页 storage 变化时探测
     * auth-center 侧 IdP 会话。**仅在确认失去会话时**才清本地并回跳登录页
     * （fail-safe：探针失败=网络抖动/超时/CORS 一律保持现状，绝不误登出）。
     *
     * 幂等：重复调用返回既有单例，不会叠加定时器。
     *
     * @param {object} [options] 透传 SessionWatcherOptions（intervalMs/confirmCount/loginUrl 等）
     * @returns {object|null} 监视器句柄（含 stop()），组件未就绪时返回 null
     */
    function startSessionWatcher(options) {
        if (!sso) return null;
        if (watcher) return watcher;
        try {
            var watcherOpts = Object.assign({}, options || {}, {
                // 身份一致性守卫（auth-components 0.5.4）：IdP 会话是谁，本地会话就应是谁。
                // 共享浏览器换人登录时本地旧 token 还在 → 探针身份 ≠ 本地身份 → 静默重换票。
                getLocalIdentity: function () {
                    try {
                        var claims = MICRO.decodeOidcClaims(MICRO.getToken() || '');
                        return claims && claims.sub ? String(claims.sub) : null;
                    } catch (e2) { return null; }
                },
                onIdentityMismatch: function () {
                    try { client().renewByReauthorize(); } catch (e3) { /* 组件异常时由兜底清态 */ }
                }
            });
            watcher = client().watchSession(watcherOpts);
            return watcher;
        } catch (e) {
            if (global.console && console.warn) console.warn('[activecode] 会话监视启动失败', e);
            return null;
        }
    }

    /** 停止会话监视器（登出/切账号前调用，避免旧监视器误跳转） */
    function stopSessionWatcher() {
        if (!watcher) return;
        try {
            watcher.stop();
        } catch (e) {
            /* ignore */
        }
        watcher = null;
    }

    // ------------------------------------------------------------------
    // 登出 / 续期
    // ------------------------------------------------------------------

    /** 是否处于 OIDC 登录态（用于 401 分支判据：OIDC 走静默重授权，老式登录跳登录页） */
    function isOidc() {
        if (!MICRO || typeof MICRO.isOidcToken !== 'function') return false;
        try {
            return !!MICRO.isOidcToken();
        } catch (e) {
            return false;
        }
    }

    /**
     * 统一登出（SLO）：销毁 IdP 会话 + 清本地，然后回跳登录页。
     *
     * 与旧实现的关键区别：旧实现只清 localStorage，IdP 会话仍在，
     * 下一个应用/下一次静默免登会把你**直接免密登回去**，表现为「登不掉」。
     *
     * @param {{postLogoutRedirectUri?: string}} [options]
     */
    function logout(options) {
        var opts = options || {};
        // 先停会话监视，避免登出过程中监视器触发一次多余探针/跳转
        stopSessionWatcher();
        var c = client();
        // 顺序：先取 id_token（登出后本地就没了）→ 清本地 → 跳 SLO
        var idToken = null;
        try {
            idToken = c.getIdToken();
        } catch (e) {
            idToken = null;
        }
        try {
            for (var i = 0; i < LEGACY_KEYS.length; i++) {
                localStorage.removeItem(LEGACY_KEYS[i]);
            }
        } catch (e) {
            /* ignore */
        }
        c.logout({
            idTokenHint: idToken || undefined,
            postLogoutRedirectUri: opts.postLogoutRedirectUri || LOGIN_URL,
            state: opts.state
        });
    }

    /** 仅清本地凭据（不碰 IdP 会话） */
    function clearLocalAuth() {
        stopSessionWatcher();
        clearLocalState();
    }

    /**
     * 静默续期：重跑一次授权（IdP 会话在则秒回新 code，用户无感；不返回）。
     *
     * ★ 带防回环护栏：10 秒内已经重授权过一次、回来后**仍然** 401，
     *   说明不是 token 过期而是会话/后端出了真问题。此时不再重试，
     *   直接清本地回登录页，避免 `/authorize ↔ main.html` 无限弹跳。
     *
     * @param {string} [redirect] 默认回到当前页
     */
    function renew(redirect) {
        var now = Date.now();
        var last = 0;
        try {
            last = parseInt(sessionStorage.getItem(RENEW_GUARD_KEY) || '0', 10) || 0;
        } catch (e) {
            last = 0;
        }
        if (last && now - last < RENEW_GUARD_MS) {
            stopSessionWatcher();
            clearLocalState();
            global.location.href = LOGIN_URL + '?reauth=1';
            return Promise.reject(new Error('SSO 会话反复失效，已回退登录页'));
        }
        try {
            sessionStorage.setItem(RENEW_GUARD_KEY, String(now));
        } catch (e) {
            /* ignore */
        }
        return client().renew(redirect || (global.location.pathname + global.location.search));
    }

    /** 构建登出 URL（需要自己控制跳转时机时用） */
    function buildLogoutUrl(options) {
        return client().buildLogoutUrl(options || {});
    }

    global.ActiveCodeSSO = {
        // 兼容旧调用
        startSsoLogin: startSsoLogin,
        handleSsoCallback: handleSsoCallback,
        // 紧密型能力
        bootstrapLoginPage: bootstrapLoginPage,
        probeSession: probeSession,
        silentSignIn: silentSignIn,
        logout: logout,
        buildLogoutUrl: buildLogoutUrl,
        renew: renew,
        isOidc: isOidc,
        clearLocalAuth: clearLocalAuth,
        // 会话监视（SLO 跨应用联动）
        startSessionWatcher: startSessionWatcher,
        stopSessionWatcher: stopSessionWatcher,
        /** 组件版本，线上排障用 */
        version: (MICRO && MICRO.version) || 'unknown',
        /** 组件是否加载成功 */
        ready: !!sso
    };
})(window);
