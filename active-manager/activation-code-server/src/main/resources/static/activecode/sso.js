/*
 * activecode 统一认证 SSO（OIDC authorization_code + PKCE）
 * 移植自 kb-web src/utils/sso.ts，改为原生 JS（本服务为静态 HTML，无构建）。
 *
 * 兼容内网 http（crypto.subtle 在非安全上下文不可用）：
 *   - verifier 用 crypto.getRandomValues（非安全上下文也可用）
 *   - challenge 优先用 crypto.subtle.digest(SHA-256)，不可用时回退纯 JS SHA-256
 *
 * 流程：startSsoLogin() 跳转 auth-center → sso-callback.html 用 code+verifier 换 token →
 *       POST /activecode/api/auth/sso-login 建立后端 session → 跳回业务页。
 */
(function (global) {
    'use strict';

    var OIDC_ISSUER = 'https://auth.marschat.online';
    var OIDC_CLIENT_ID = 'marschat-activecode';
    var OIDC_REDIRECT_URI = global.location.origin + '/activecode/sso-callback';
    var OIDC_SCOPE = 'openid profile';

    var STATE_KEY = 'activecode_sso_state';
    var VERIFIER_KEY = 'activecode_sso_verifier';
    var REDIRECT_KEY = 'activecode_sso_redirect';

    // ---------- 基础工具 ----------
    function b64url(bytes) {
        var bin = '';
        for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
        return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    }

    function randomBytes(n) {
        var buf = new Uint8Array(n);
        if (global.crypto && global.crypto.getRandomValues) {
            global.crypto.getRandomValues(buf);
        } else {
            for (var i = 0; i < n; i++) buf[i] = Math.floor(Math.random() * 256);
        }
        return buf;
    }

    function randomVerifier() {
        return b64url(randomBytes(32));
    }

    function randomState() {
        return b64url(randomBytes(16));
    }

    // ---------- 纯 JS SHA-256（内网 http 回退用） ----------
    function ror(x, n) { return (x >>> n) | (x << (32 - n)); }

    function sha256Bytes(msg) {
        var K = new Uint32Array([
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
        ]);
        var h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a,
            h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;
        var l = msg.length;
        var blockLen = Math.ceil((l + 9) / 64) * 64;
        var m = new Uint8Array(blockLen);
        m.set(msg);
        m[l] = 0x80;
        var bitLen = l * 8;
        m[blockLen - 4] = (bitLen >>> 24) & 0xff;
        m[blockLen - 3] = (bitLen >>> 16) & 0xff;
        m[blockLen - 2] = (bitLen >>> 8) & 0xff;
        m[blockLen - 1] = bitLen & 0xff;
        var w = new Uint32Array(64);
        for (var off = 0; off < blockLen; off += 64) {
            for (var t = 0; t < 16; t++) {
                var j = off + t * 4;
                w[t] = (m[j] << 24) | (m[j + 1] << 16) | (m[j + 2] << 8) | m[j + 3];
            }
            for (var t2 = 16; t2 < 64; t2++) {
                var s0 = ror(w[t2 - 15], 7) ^ ror(w[t2 - 15], 18) ^ (w[t2 - 15] >>> 3);
                var s1 = ror(w[t2 - 2], 17) ^ ror(w[t2 - 2], 19) ^ (w[t2 - 2] >>> 10);
                w[t2] = (w[t2 - 16] + s0 + w[t2 - 7] + s1) | 0;
            }
            var a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, hh = h7;
            for (var tt = 0; tt < 64; tt++) {
                var S1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25);
                var ch = (e & f) ^ ((~e) & g);
                var temp1 = (hh + S1 + ch + K[tt] + w[tt]) | 0;
                var S0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22);
                var maj = (a & b) ^ (a & c) ^ (b & c);
                var temp2 = (S0 + maj) | 0;
                hh = g; g = f; f = e; e = (d + temp1) | 0; d = c; c = b; b = a; a = (temp1 + temp2) | 0;
            }
            h0 = (h0 + a) | 0; h1 = (h1 + b) | 0; h2 = (h2 + c) | 0; h3 = (h3 + d) | 0;
            h4 = (h4 + e) | 0; h5 = (h5 + f) | 0; h6 = (h6 + g) | 0; h7 = (h7 + hh) | 0;
        }
        var out = new Uint8Array(32);
        var hs = [h0, h1, h2, h3, h4, h5, h6, h7];
        for (var i = 0; i < 8; i++) {
            out[i * 4] = (hs[i] >>> 24) & 0xff;
            out[i * 4 + 1] = (hs[i] >>> 16) & 0xff;
            out[i * 4 + 2] = (hs[i] >>> 8) & 0xff;
            out[i * 4 + 3] = hs[i] & 0xff;
        }
        return out;
    }

    function pkceChallenge(verifier) {
        if (global.crypto && global.crypto.subtle && global.crypto.subtle.digest) {
            return global.crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
                .then(function (digest) { return b64url(new Uint8Array(digest)); });
        }
        return Promise.resolve(b64url(sha256Bytes(new TextEncoder().encode(verifier))));
    }

    // ---------- SSO 流程 ----------
    function startSsoLogin(redirect) {
        redirect = redirect || '/activecode/main.html';
        var state = randomState();
        var verifier = randomVerifier();
        sessionStorage.setItem(STATE_KEY, state);
        sessionStorage.setItem(VERIFIER_KEY, verifier);
        sessionStorage.setItem(REDIRECT_KEY, redirect);
        return pkceChallenge(verifier).then(function (challenge) {
            var params = new URLSearchParams({
                client_id: OIDC_CLIENT_ID,
                redirect_uri: OIDC_REDIRECT_URI,
                response_type: 'code',
                scope: OIDC_SCOPE,
                state: state,
                code_challenge: challenge,
                code_challenge_method: 'S256'
            });
            global.location.assign(OIDC_ISSUER + '/oauth2/authorize?' + params.toString());
        });
    }

    function decodeClaims(token) {
        try {
            var p = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
            var json = decodeURIComponent(escape(atob(p)));
            return JSON.parse(json);
        } catch (e) {
            return {};
        }
    }

    function handleSsoCallback() {
        var params = new URLSearchParams(global.location.search);
        var code = params.get('code');
        var state = params.get('state');
        var err = params.get('error');
        if (err) throw new Error('统一认证拒绝: ' + (params.get('error_description') || err));
        if (!code) throw new Error('授权回调缺少 code');

        var savedState = sessionStorage.getItem(STATE_KEY);
        var verifier = sessionStorage.getItem(VERIFIER_KEY);
        if (!savedState || savedState !== state) throw new Error('state 校验失败，请重新发起登录');
        if (!verifier) throw new Error('PKCE 凭据丢失，请重新发起登录');

        var body = new URLSearchParams({
            grant_type: 'authorization_code',
            code: code,
            redirect_uri: OIDC_REDIRECT_URI,
            client_id: OIDC_CLIENT_ID,
            code_verifier: verifier
        });
        return fetch(OIDC_ISSUER + '/oauth2/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: body.toString()
        }).then(function (res) {
            if (!res.ok) {
                return res.text().catch(function () { return ''; }).then(function (detail) {
                    throw new Error('换取令牌失败(HTTP ' + res.status + '): ' + detail.slice(0, 160));
                });
            }
            return res.json();
        }).then(function (data) {
            sessionStorage.removeItem(STATE_KEY);
            sessionStorage.removeItem(VERIFIER_KEY);
            var redirect = sessionStorage.getItem(REDIRECT_KEY) || '/activecode/main.html';
            sessionStorage.removeItem(REDIRECT_KEY);

            var username = '';
            if (data.id_token) {
                var claims = decodeClaims(data.id_token);
                username = claims.preferred_username || claims.unique_name || claims.sub || claims.name || '';
            }
            localStorage.setItem('activecode_oidc_access', data.access_token || '');
            if (data.refresh_token) localStorage.setItem('activecode_oidc_refresh', data.refresh_token);
            if (data.id_token) localStorage.setItem('activecode_oidc_id', data.id_token);
            localStorage.setItem('activecode_sso_user', username);

            // 后端需对 RS256 access_token 验签，必须一并发过去
            return fetch('/activecode/api/auth/sso-login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: username, access_token: data.access_token || '' })
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
                return { redirect: redirect, username: username || loginData.username };
            });
        });
    }

    global.ActiveCodeSSO = {
        startSsoLogin: startSsoLogin,
        handleSsoCallback: handleSsoCallback
    };
})(window);
