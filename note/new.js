// ============================
// 纯前端黑白名单检查函数（同时满足白名单且不在黑名单）
// 参数顺序：(eventUrl, options, callback)
// ============================
function checkPageAllowed(eventUrl, options, callback) {

    // ---------- 辅助函数（保持不变） ----------
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

    function _safeMatch(rule, target) {
        if (!rule || typeof rule !== "string") return false;
        rule = $.trim(rule); // 已有 jQuery 依赖，直接用 $.trim 兼容 IE6+
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

    // ---------- 参数归一化（保持不变） ----------
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

    // 提取参数（priority 参数保留但不再参与逻辑，仅为兼容调用）
    var priority = (options && options.priority) || "black";   // 保留但无用
    var blackListKey = (options && options.blackListKey) || "blackList";
    var whiteListKey = (options && options.whiteListKey) || "whiteList";

    // ---------- 缓存（保持不变） ----------
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

    // ---------- 新核心逻辑：同时满足白名单且不在黑名单 ----------
    // 并行获取黑白名单，提高效率
    var whiteList = null;
    var blackList = null;

    function finalCheck() {
        if (whiteList === null || blackList === null) return;

        // 判断是否在白名单中
        var inWhite = false;
        for (var i = 0; i < whiteList.length; i++) {
            if (_safeMatch(whiteList[i], eventUrl)) {
                inWhite = true;
                break;
            }
        }

        // 判断是否在黑名单中
        var inBlack = false;
        for (var j = 0; j < blackList.length; j++) {
            if (_safeMatch(blackList[j], eventUrl)) {
                inBlack = true;
                break;
            }
        }

        // 最终结果：必须在白名单中，且不在黑名单中
        var allowed = inWhite && !inBlack;
        try {
            callback(allowed);
        } catch (e) {
            _safeLog("用户回调异常:", e);
        }
    }

    getConfig(whiteListKey, function(list) {
        whiteList = list;
        finalCheck();
    });
    getConfig(blackListKey, function(list) {
        blackList = list;
        finalCheck();
    });
}

// 导出给 require 使用（保留原导出语句）
if (typeof module !== "undefined" && module.exports) {
    module.exports = checkPageAllowed;
}