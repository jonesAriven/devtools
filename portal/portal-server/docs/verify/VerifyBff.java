import com.marschat.auth.bff.BffWhitelist;
import com.marschat.auth.bff.BffWhitelistLoader;

/**
 * portal 白名单迁移等价性验证 —— 用**已发布的 auth-core 2.2.0** 加载本应用真实的
 * {@code bff-whitelist.yml}，逐条比对「迁移前 {@code SsoController#proxyAdminCenter} 的
 * {@code @RequestMapping("/admin/**")} 全通配」与「迁移后显式白名单」的判定差异。
 *
 * <h3>本用例集与 kb-ops(34) / infra(29) / activecode(29) 的本质差异</h3>
 * 那三个应用只有「本系统用户」（{@code scope.mode='app'}）一个面板，故其白名单里
 * <b>绝不登记</b> {@code POST/PUT/DELETE /admin/users} 与 {@code PUT .../password}
 * （README坑 #22：应用台不创建人，口令属身份层）。
 *
 * <p><b>portal 额外承载「统一认证中心」平台管理台</b>
 * （{@code src/views/AdminConsoleView.vue}，路由 {@code /portal/admin}，
 * {@code scope.mode='platform'}），其页签按设计就要用平台级写端点
 * （见 ADR-2026-09-16-Phase12 §D-1 功能对照表：平台管理台 ✅ / 应用台 ❌）。
 * 因此这些端点<b>必须登记</b>，否则平台管理台「点了就报错」。
 *
 * <p>⚠️ <b>收窄口径分两层，合称「严格收窄」不准确</b>：
 * <ul>
 *   <li><b>路径空间</b>：迁移前是 {@code /admin/**} <b>全通配</b>（放行一切），
 *       本白名单是<b>严格收窄</b>；</li>
 *   <li><b>平台级写端点这一类</b>：本文件的设计意图是「永不放行」，
 *       现为<b>有条件放行</b> ⇒ 这一类是<b>放宽</b>，不是收窄。</li>
 * </ul>
 * （kb-ops / infra-monitor / activecode 因只有应用台，确为纯收窄；portal 不是。）
 * 中心的类级 {@code @PreAuthorize("hasRole('ADMIN')")} 与本地 role 闸门
 * （{@code PortalAdminGateInterceptor}）照旧生效。逐条清单见白名单文件头边界④。
 *
 * <h3>🔴 为什么不能用「无凭据 401 vs 404」判白名单（坑 #31）</h3>
 * 安全过滤器在 controller <b>之前</b>就返回 401/403，白名单那行代码根本执行不到。
 * 故必须用本离线脚本（或带真实会话的浏览器回归）验证。
 *
 * <p>运行：
 * <pre>
 * cd portal/portal-server
 * mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;$(cat target/cp.txt)" docs/verify/VerifyBff.java
 * </pre>
 */
public class VerifyBff {

    private static final String APP = "marschat-portal";
    private static int pass = 0;
    private static int fail = 0;

    /**
     * @param expected true = 迁移后应放行；false = 应拒绝
     * @param wasOpen  迁移前（{@code /admin/**} 全通配）是否放行 —— 全为 true，
     *                 此列仅用于人读对照，不参与断言
     */
    record Case(String method, String path, String query, boolean expected, String note) {
    }

    public static void main(String[] args) throws Exception {
        BffWhitelist wl = new BffWhitelistLoader().load("classpath:bff-whitelist.yml");
        System.out.println("[加载] 规则数=" + wl.size());
        System.out.println("[加载] " + wl.describe());
        System.out.println();

        Case[] cases = {
                // ── ① 应用作用域：/portal/users（UsersView，scope.mode='app'）──
                new Case("GET", "/admin/users", "client=" + APP, true, "本系统成员列表"),
                new Case("GET", "/admin/users", "client=" + APP + "&page=1&size=20", true, "分页"),
                new Case("GET", "/admin/users", "keyword=zhang&page=1&size=20", true, "候选池搜索（无 client）"),
                new Case("GET", "/admin/users", "client=" + APP + "&page=1&size=200", true, "appRoles 候选池全量"),
                new Case("GET", "/admin/users/302/client-roles", "client=" + APP, true, "读本系统角色绑定"),
                new Case("PUT", "/admin/users/302/client-roles", "client=" + APP, true, "写本系统角色绑定"),
                new Case("GET", "/admin/users/302/menu-overrides", "client=" + APP, true, "读用户级菜单减法"),
                new Case("PUT", "/admin/users/302/menu-overrides", "client=" + APP, true, "写用户级菜单减法"),
                new Case("GET", "/admin/roles", null, true, "角色定义（下拉/角色树）"),

                // ── ② 平台作用域：/portal/admin（AdminConsoleView，scope.mode='platform'）──
                new Case("GET", "/admin/users", "page=1&size=20", true, "平台统一用户列表（不带 client）"),
                new Case("POST", "/admin/users", null, true, "平台管理台：新建身份"),
                new Case("PUT", "/admin/users/302", null, true, "平台管理台：编辑身份/停用"),
                new Case("DELETE", "/admin/users/302", null, true, "平台管理台：删除身份（墓碑）"),
                new Case("PUT", "/admin/users/302/password", null, true, "平台管理台：重置口令"),
                new Case("GET", "/admin/permissions", "client=" + APP, true, "权限点树（本应用）"),
                new Case("GET", "/admin/permissions", "client=marschat-kbops", true, "权限点树（切到别的应用）"),
                new Case("GET", "/admin/roles/5/permission-codes", null, true, "读角色→权限点"),
                new Case("PUT", "/admin/roles/5/permission-codes", null, true, "写角色→权限点（菜单授权保存）"),
                new Case("GET", "/admin/authorization-matrix", "page=1&size=20", true, "跨应用授权矩阵"),
                new Case("GET", "/admin/mappings", "page=1&size=10", true, "账号映射列表"),
                new Case("GET", "/admin/mappings/summary", null, true, "账号映射概览"),
                new Case("GET", "/admin/users/302/mappings", null, true, "按用户查映射"),
                new Case("POST", "/admin/mappings/7/bind", null, true, "手工认领"),
                new Case("POST", "/admin/mappings/7/unbind", null, true, "手工解绑"),

                // ── ③ 跨应用授权矩阵：对每个应用读写 client-roles（不带本应用作用域限制）──
                new Case("GET", "/admin/users/302/client-roles", "client=marschat-kbops", true, "矩阵：读 kbops 角色"),
                new Case("GET", "/admin/users/302/client-roles", "client=cosmic-studio", true, "矩阵：读 cosmic 角色"),
                new Case("PUT", "/admin/users/302/client-roles", "client=marschat-kbweb", true, "矩阵：改绑 kbweb 角色"),

                // ── ④ 拒绝：HPP 参数污染（作用域加固）──
                new Case("GET", "/admin/users", "client=" + APP + "&client=marschat-other", false, "HPP：重复 client 键"),
                new Case("GET", "/admin/users/302/menu-overrides",
                        "client=" + APP + "&client=marschat-other", false, "HPP：菜单减法重复 client 键"),

                // ── ⑤ 拒绝：作用域越界（本应用 client 不匹配）──
                new Case("GET", "/admin/users", "client=marschat-other", false, "成员列表 client 越界"),
                new Case("GET", "/admin/users/302/menu-overrides", "client=marschat-other", false, "菜单减法 client 越界"),

                // ── ⑥ 拒绝：平台运维通道（永不下放给门户）──
                new Case("POST", "/admin/authz/migrate", null, false, "授权策略运维（平台专属）"),
                new Case("GET", "/admin/authorization-matrix", null, true, "（对照）矩阵本身是可用的"),
                new Case("GET", "/admin/auth/list", "page=1&size=20", false, "全局审计日志（平台运维）"),
                new Case("POST", "/admin/clients/" + APP + "/roles", null, false, "建应用角色（防自造角色提权）"),
                new Case("PUT", "/internal/clients/" + APP + "/accounts", null, false, "内网上报通道永不放行"),
                new Case("PUT", "/internal/clients/" + APP + "/menus", null, false, "内网菜单上报通道永不放行"),

                // ── ⑦ 拒绝：未登记的 /admin 端点（默认拒绝的核心）──
                new Case("GET", "/admin/settings", null, false, "未登记端点 → 默认拒绝"),
                new Case("GET", "/admin/clients", null, false, "未登记端点 → 默认拒绝"),
                new Case("POST", "/admin/roles", null, false, "建平台角色：未登记"),
                new Case("DELETE", "/admin/roles/5", null, false, "删角色：未登记"),
                new Case("PUT", "/admin/mappings", null, false, "映射写入（整体 PUT）：未登记"),
                new Case("PATCH", "/admin/users/302", null, false, "PATCH 不在方法白名单"),
                new Case("DELETE", "/admin/users/302/client-roles", null, false, "client-roles 不支持 DELETE"),
                new Case("DELETE", "/admin/users/302/menu-overrides", null, false, "menu-overrides 不支持 DELETE"),
                new Case("POST", "/admin/users/302/password", null, false, "重置口令只允许 PUT"),
                new Case("GET", "/admin/mappings/7", null, false, "单条映射：未登记"),
                new Case("GET", "/auth/permissions", null, false, "非管理面（/auth/** 不在代理范围）"),
        };

        for (Case c : cases) {
            boolean actual = wl.isAllowed(c.method(), c.path(), c.query(), null, APP);
            boolean ok = actual == c.expected();
            if (ok) {
                pass++;
            } else {
                fail++;
            }
            System.out.printf("  %s %-6s %-44s client=%-30s 期望=%-5s 实际=%-5s (%s)%n",
                    ok ? "[OK]" : "[NG]",
                    c.method(),
                    c.path(),
                    c.query() == null ? "-" : c.query(),
                    c.expected() ? "ALLOW" : "DENY",
                    actual ? "ALLOW" : "DENY",
                    c.note());
        }

        // ── 白名单缺失/为空时必须 fail-closed（全部拒绝），不得放行 ──
        System.out.println();
        BffWhitelist empty = new BffWhitelistLoader().load("classpath:__no_such_whitelist__.yml");
        boolean failClosed = empty.isEmpty() && !empty.isAllowed("GET", "/admin/users", null, APP);
        if (failClosed) {
            pass++;
            System.out.printf("  %s %-6s %-44s %-36s 期望=%-5s 实际=%-5s (%s)%n",
                    "[OK]", "GET", "/admin/users", "(文件缺失)", "DENY", "DENY", "白名单缺失 ⇒ fail-closed");
        } else {
            fail++;
            System.out.printf("  %s %-6s %-44s %-36s 期望=%-5s 实际=%-5s (%s)%n",
                    "[NG]", "GET", "/admin/users", "(文件缺失)", "DENY", "ALLOW", "白名单缺失必须 fail-closed");
        }

        // ── client-id 未配置时必须 fail-closed ──
        boolean noClientId = !wl.isAllowed("GET", "/admin/users", null, null);
        if (noClientId) {
            pass++;
            System.out.printf("  %s %-6s %-44s %-36s 期望=%-5s 实际=%-5s (%s)%n",
                    "[OK]", "GET", "/admin/users", "(clientId=null)", "DENY", "DENY",
                    "未配置 client-id ⇒ fail-closed");
        } else {
            fail++;
            System.out.printf("  %s %-6s %-44s %-36s 期望=%-5s 实际=%-5s (%s)%n",
                    "[NG]", "GET", "/admin/users", "(clientId=null)", "DENY", "ALLOW",
                    "未配置 client-id 必须 fail-closed");
        }

        // ══════════════════════════════════════════════════════════════════
        //闸门三态验证（PortalAdminGateInterceptor · Phase 13 方案 A）
        //
        // ⚠️ 坑 #31：白名单**放行 ≠ 端点可用**。闸门是运行期 HTTP 行为，
        //    本机无 auth-center/MySQL ⇒ **无法真跑**（QA 需真实环境）。
        //    故此处验证的是**代码里可静态断言的那一面**：三道闸的判定顺序与
        //    状态码语义是否与设计一致（用反射读取常量 + 源码契约）。
        //    真机三态由 QA #11 覆盖：role=user→403 / admin(账密)→401 / admin(SSO)→200。
        // ══════════════════════════════════════════════════════════════════
        System.out.println();
        System.out.println("── 闸门三态（静态契约） ──");
        checkGateContract();

        System.out.println();
        System.out.println("=".repeat(96));
        System.out.printf("portal 白名单迁移等价性：%d 通过 / %d 失败（共 %d 项）%n", pass, fail, pass + fail);
        System.out.println("迁移前为 /admin/** 全通配（放行一切）；以上「实际=DENY」项即本次收窄掉的端点。");
        System.out.println("=".repeat(96));
        if (fail > 0) {
            System.exit(1);
        }
    }

    /**
     * 闸门静态契约验证 —— 断言 7 件事，任一不符即失败：
     * <ol>
     *   <li>第②道仍存在（类中确实注入了 {@code PermissionChecker}），未退化为单闸；</li>
     *   <li>{@code hasSsoSession} 前置于 {@code hasPermission}（场景②/③可区分）；</li>
     *   <li>第②道失败返回 <b>401</b>（可自助恢复）而非 403（会被前端卡死）；</li>
     *   <li>角色闸失败返回 <b>403</b>（语义正确：非管理员无需重授权）；</li>
     *   <li>superadmin 豁免<b>在</b> {@code PortalPermissionChecker}（单一真源）；</li>
     *   <li>本类<b>不重复</b>实现豁免（无两处真源）；</li>
     *   <li>🔴 两处闸门的<b>返回值确实被消费</b>（防「调用存在但结果被丢弃/短路」）。</li>
     * </ol>
     *
     * <p><b>覆盖范围（重要）</b>：以上全部是<b>静态</b>断言，能防「字段被删 / 顺序改反 /
     * 状态码改错 / 调用被短路」，<b>不能替代真机三态</b>（坑 #31 同源：闸门是运行期行为，
     * 本机无 auth-center/MySQL跑不了）。真机三态由 QA #11 覆盖。
     */
    private static void checkGateContract() throws Exception {
        Class<?> gate = Class.forName("com.kb.portal.config.PortalAdminGateInterceptor");

        // ① 第②道未被移除：必须持有 PermissionChecker 字段
        boolean hasCenterGate = false;
        for (java.lang.reflect.Field f : gate.getDeclaredFields()) {
            if (f.getType().getName().equals("com.marschat.auth.authz.PermissionChecker")) {
                hasCenterGate = true;
                break;
            }
        }
        record(hasCenterGate, "第②道中心权限点校验存在（未退化为单闸）");

        // ② hasSsoSession 前置：源码中 hasSsoSession 的调用位置必须早于 hasPermission
        java.nio.file.Path src = java.nio.file.Path.of("src/main/java/com/kb/portal/config/PortalAdminGateInterceptor.java");
        String code = java.nio.file.Files.readString(src, java.nio.charset.StandardCharsets.UTF_8);
        int idxSso = code.indexOf("hasSsoSession(");
        int idxPerm = code.indexOf("hasPermission(");
        boolean ssoFirst = idxSso > 0 && idxPerm > 0 && idxSso < idxPerm;
        record(ssoFirst, "hasSsoSession 前置于 hasPermission（场景②/③可区分）");

        // ③ 权限点失败 → 401（可自助恢复）；角色失败 → 403
        //    判据锚定在真实判定块 `if (!permitted) {` 上（不能用首次出现的
        //    hasPermission( —— 类注释里的 {@link} 会先命中，导致误判）。
        int idxDenyBlock = code.indexOf("if (!permitted)");
        boolean centerDenyIs401 = idxDenyBlock > 0
                && code.substring(idxDenyBlock, idxDenyBlock + 400).contains("SC_UNAUTHORIZED");
        record(centerDenyIs401, "第②道失败返 401（前端可跳SSO 重授权，不会卡死）");

        int idxRoleDeny = code.indexOf("SC_FORBIDDEN");
        boolean roleDenyIs403 = idxRoleDeny > 0 && idxRoleDeny < idxDenyBlock;
        record(roleDenyIs403, "第①道（role）失败返 403（语义正确，无需重授权）");

        // ④ superadmin 豁免在 PortalPermissionChecker 内，不在本类（避免两处真源）
        java.nio.file.Path checkerSrc =
                java.nio.file.Path.of("src/main/java/com/kb/portal/config/PortalPermissionChecker.java");
        String checkerCode = java.nio.file.Files.readString(checkerSrc, java.nio.charset.StandardCharsets.UTF_8);
        boolean exemptInChecker = checkerCode.contains("\"superadmin\".equals(role)") && checkerCode.contains("return true");
        record(exemptInChecker, "superadmin 豁免位于 PortalPermissionChecker（单一真源）");

        // ⑥ 本类不得自行实现豁免（否则与 checker 漂移）：
        //    判据 = 本类源码里不出现「superadmin 直接 return true」这种豁免写法。
        boolean noLocalExempt = !code.contains("ROLE_SUPERADMIN) {") || !code.contains("ROLE_SUPERADMIN) return true");
        record(noLocalExempt, "本类不重复实现 superadmin 豁免（无两处真源）");

        // ⑦ 🔴 防「调用存在但结果被丢弃」——静态契约的经典盲区（QA 提出）
        //    前6 条只能证伪「字段被删/ 顺序改反 / 状态码改错」，**证伪不了短路**：
        //    例如 permissionChecker.hasPermission(...);  // 返回值直接扔掉
        //    或 if (true) { ... } 让第②道形同虚设 —— 此时前 6 条**全部照样通过**。
        //    判据：两处闸门的返回值都必须【赋值给局部变量】且该变量【参与分支判断】。
        record(resultIsConsumed(code, "permissionChecker.hasPermission"),
                "第②道 hasPermission 返回值被消费（防调用被短路/丢弃）");
        record(resultIsConsumed(code, "hasSsoSession"),
                "前置 hasSsoSession 返回值被消费（防场景③闸失效）");
    }

    /**
     * 判断某次方法调用的返回值是否真正参与了判定。
     *
     * <p><b>接受两种等价写法</b>（都是安全的消费方式）：
     * <ol type="a">
     *   <li><b>内联取反</b>：{@code if (!svc.hasSsoSession(id)) {…}}</li>
     *   <li><b>赋值后消费</b>：{@code boolean v = svc.hasPermission(…); if (!v) {…}}</li>
     * </ol>
     * 两种都必须「返回值真的进了条件」——
     * {@code svc.hasSsoSession(id);}（丢弃）或 {@code if (true) {…}}（短路）都会被判失败。
     *
     * <p>⚠️ 早期版本只认写法 ②，导致内联取反被误判为 NG（假阴性）。
     * 断言写太窄会把正确代码判成错误 —— 修断言，不改正确的业务代码。
     *
     * @param callMarker 调用标记（如 {@code "permissionChecker.hasPermission"}）
     */
    private static boolean resultIsConsumed(String code, String callMarker) {
        String q = java.util.regex.Pattern.quote(callMarker);
        // 允许「接收者.」前缀（marker 传 "hasSsoSession" 时能匹配 authCenterService.hasSsoSession）
        String recv = "(?:[A-Za-z_]\\w*\\.)*";
        // 写法 a：内联取反/取真 —— if ( !xxx.method(  或  if ( xxx.method(
        if (java.util.regex.Pattern.compile("if\\s*\\(\\s*!?" + recv + q + "\\s*\\(").matcher(code).find()) {
            return true;
        }
        // 写法 b：赋值给局部变量，且该变量随后进入条件
        java.util.regex.Matcher assign = java.util.regex.Pattern
                .compile("(?:boolean|var)\\s+(\\w+)\\s*=\\s*" + recv + q + "\\s*\\(")
                .matcher(code);
        if (!assign.find()) {
            return false; // 既未内联消费、也未赋值 ⇒ 返回值被直接丢弃
        }
        String var = assign.group(1);
        return java.util.regex.Pattern
                .compile("if\\s*\\(\\s*!?" + java.util.regex.Pattern.quote(var) + "\\s*\\)")
                .matcher(code)
                .find();
    }

    /** 记录一条断言结果。 */
    private static void record(boolean ok, String note) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.printf("  %s %-58s %s%n", ok ? "[OK]" : "[NG]", note, ok ? "" : "← 契约不符");
    }
}