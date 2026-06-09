// ============================
// 纯前端黑白名单检查函数
// 参数顺序：(eventUrl, options, callback)
// ============================

function checkPageAllowed(eventUrl, options, callback) {
    // ---------- 辅助函数 ----------
    function _safeLog(msg, err) {
        var hasConsole = typeof console !== "undefined" && typeof console.error === "function";
        try { if (hasConsole) console.error(msg, err); } catch (e) {}
    }

    function _safeStringify(obj) {
        if (typeof JSON !== "undefined" && typeof JSON.stringify === "function") {
            return JSON.stringify(obj);
        }
        if (typeof obj !== "object") return String(obj);
        var pairs = [];
        for (var k in obj) {
            if (obj.hasOwnProperty(k)) {
                var v = obj[k];
                var key = '"' + k.replace(/"/g, '\\"') + '"';
                var value = typeof v === "string" ? '"' + v.replace(/"/g, '\\"') + '"' : String(v);
                pairs.push(key + ":" + value);
            }
        }
        return "{" + pairs.join(",") + "}";
    }

    function _trim(str) {
        if (typeof str.trim === "function") return str.trim();
        return str.replace(/^\s+|\s+$/g, "");
    }

    function _safeMatch(rule, target) {
        if (!rule || typeof rule !== "string") return false;
        rule = _trim(rule);
        if (rule === "") return false;
        if (rule.indexOf("*") !== -1) {
            var parts = rule.split("*");
            if (parts.length === 2) {
                var prefix = parts[0];
                var suffix = parts[1];
                if (prefix && suffix) {
                    return target.indexOf(prefix) === 0 && target.indexOf(suffix) === target.length - suffix.length;
                } else if (prefix) {
                    return target.indexOf(prefix) === 0;
                } else if (suffix) {
                    return target.indexOf(suffix) === target.length - suffix.length;
                }
            }
            return target.indexOf(rule.replace(/\*/g, "")) !== -1;
        }
        return target.indexOf(rule) !== -1;
    }

    // ---------- 参数归一化 ----------
    // 支持两种调用方式：checkPageAllowed(url, callback) 或 checkPageAllowed(url, options, callback)
    if (typeof options === "function") {
        callback = options;
        options = undefined;
    }
    if (typeof callback !== "function") return;

    if (typeof eventUrl !== "string" || !eventUrl) {
        callback(false);
        return;
    }

    // 检查 jQuery 依赖
    if (typeof $ === "undefined" || typeof $.ajax !== "function") {
        _safeLog("jQuery 未加载或 $.ajax 不可用");
        callback(false);
        return;
    }

    var priority = (options && options.priority) || "black";
    var blackListKey = (options && options.blackListKey) || "blackList";
    var whiteListKey = (options && options.whiteListKey) || "whiteList";

    // ---------- 缓存（挂载静态属性）----------
    if (!checkPageAllowed.cache) checkPageAllowed.cache = {};
    var _cache = checkPageAllowed.cache;
    function now() { return new Date().getTime(); }

    function getConfig(key, cb) {
        var timestamp = now();
        if (_cache[key] && _cache[key].expire > timestamp) {
            cb(_cache[key].value);
            return;
        }
        try {
            $.ajax({
                url: "/ngcardma/propertiesConfig/getEnvPropertiesByKey",
                contentType: "application/json;charset=UTF-8",
                type: "POST",
                data: _safeStringify({ "key": key }),
                async: true,
                timeout: 5000,
                success: function (data) {
                    try {
                        var list = [];
                        if (data && data.bean && data.bean.value) {
                            list = data.bean.value.split(",");
                        }
                        _cache[key] = { value: list, expire: now() + 5000 };
                        cb(list);
                    } catch (e) {
                        _safeLog("解析配置异常:", e);
                        cb([]);
                    }
                },
                error: function (xhr, status, err) {
                    _safeLog("获取配置接口异常:", err);
                    cb([]);
                }
            });
        } catch (e) {
            _safeLog("请求配置异常:", e);
            cb([]);
        }
    }

    var allowed = true;

    function checkBlack(next) {
        try {
            getConfig(blackListKey, function (list) {
                try {
                    for (var i = 0; i < list.length; i++) {
                        if (_safeMatch(list[i], eventUrl)) {
                            allowed = false;
                            break;
                        }
                    }
                } catch (e) {
                    _safeLog("黑名单匹配异常:", e);
                }
                next();
            });
        } catch (e) {
            _safeLog("checkBlack 异常:", e);
            next();
        }
    }

    function checkWhite(next) {
        try {
            getConfig(whiteListKey, function (list) {
                try {
                    var inWhite = false;
                    for (var i = 0; i < list.length; i++) {
                        if (_safeMatch(list[i], eventUrl)) {
                            inWhite = true;
                            break;
                        }
                    }
                    allowed = inWhite;
                } catch (e) {
                    _safeLog("白名单匹配异常:", e);
                    allowed = false;
                }
                next();
            });
        } catch (e) {
            _safeLog("checkWhite 异常:", e);
            next();
        }
    }

    function safeCallback(value) {
        try { callback(value); } catch (e) { _safeLog("用户回调异常:", e); }
    }

    if (priority === "white") {
        allowed = false;
        checkWhite(function () {
            if (allowed) safeCallback(true);
            else checkBlack(function () { safeCallback(allowed); });
        });
    } else {
        checkBlack(function () {
            if (!allowed) safeCallback(false);
            else checkWhite(function () { safeCallback(allowed); });
        });
    }
}

// ========== 调用示例（callback 在最后） ==========
var eventUrl = window.location.href;
checkPageAllowed(
    eventUrl,
    {
        priority: "black",
        blackListKey: "blackList",
        whiteListKey: "whiteList"
    },
    function (isAllowed) {
        if (typeof chaMaRecoredFlag !== "undefined" && chaMaRecoredFlag && isAllowed) {
            addEventListenerClick();
        }
    }
);

// 也支持省略 options（直接传 callback）
checkPageAllowed(eventUrl, function (isAllowed) {
    // 使用默认配置
});