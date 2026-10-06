import com.marschat.auth.bff.BffWhitelist;
import com.marschat.auth.bff.BffWhitelistLoader;

/**
 * infra-monitor 白名单迁移等价性验证 —— 用**已发布的 auth-core 2.2.0** 加载本应用真实
 * `bff-whitelist.yml`，逐条比对旧 `AdminProxyController.isAllowed()` 的判定结果。
 *
 * <p>与 kb-ops 的差异点（本应用白名单**更窄**，故意覆盖验证）：
 * <ul>
 *   <li>{@code /admin/mappings}、{@code /admin/permissions}、{@code /admin/roles/{id}/permission-codes}
 *       —— infra 未挂「账号映射 / 菜单授权」面板，故**未登记 → 必须拒绝**（kb-ops 是放行）。</li>
 *   <li>凭据模式为 {@code auto}（双会话），但白名单判定与凭据模式正交，用例一致。</li>
 * </ul>
 *
 * 运行：
 * <pre>
 * cd infra-monitor/infra-monitor-server
 * mvn -o dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/classes;$(cat target/cp.txt)" docs/verify/VerifyBff.java
 * </pre>
 */
public class VerifyBff {

    private static final String APP = "marschat-inframon";
    private static int pass = 0;
    private static int fail = 0;

    record Case(String method, String path, String query, boolean expected, String note) {
    }

    public static void main(String[] args) {
        BffWhitelist wl = new BffWhitelistLoader().load("classpath:bff-whitelist.yml");
        System.out.println("[加载] 规则数=" + wl.size());
        System.out.println("[加载] " + wl.describe());
        System.out.println();

        Case[] cases = {
                // ── 允许：本应用成员 / 角色 / 菜单减法 ──
                new Case("GET", "/admin/users", "client=" + APP, true, "成员列表（本应用作用域）"),
                new Case("GET", "/admin/users", null, true, "成员列表（缺省 client 视为放行，与旧逻辑一致）"),
                new Case("GET", "/admin/users", "page=1&size=10", true, "带分页参数"),
                new Case("GET", "/admin/users/302/client-roles", "client=" + APP, true, "读本系统角色"),
                new Case("PUT", "/admin/users/302/client-roles", "client=" + APP, true, "写本系统角色"),
                new Case("GET", "/admin/users/302/menu-overrides", "client=" + APP, true, "读菜单减法"),
                new Case("PUT", "/admin/users/302/menu-overrides", "client=" + APP, true, "写菜单减法"),
                new Case("GET", "/admin/roles", null, true, "角色定义只读"),
                new Case("GET", "/admin/clients/" + APP + "/members", null, true, "path 化成员列表"),
                new Case("POST", "/admin/clients/" + APP + "/members", null, true, "path 化加人"),
                new Case("DELETE", "/admin/clients/" + APP + "/members/302", null, true, "path 化移出"),

                // ── 拒绝：越界 client ──
                new Case("GET", "/admin/users", "client=marschat-other", false, "查询参数越界"),
                new Case("GET", "/admin/users", "client=" + APP + "&client=marschat-other", false, "HPP 参数污染"),
                new Case("GET", "/admin/users/302/client-roles", "client=other", false, "角色端点越界"),
                new Case("GET", "/admin/clients/marschat-other/members", null, false, "路径段越界"),

                // ── 拒绝：平台级写端点 ──
                new Case("POST", "/admin/users", null, false, "建身份"),
                new Case("PUT", "/admin/users/302", null, false, "改身份"),
                new Case("DELETE", "/admin/users/302", null, false, "删身份"),
                new Case("PUT", "/admin/users/302/password", null, false, "重置口令"),
                new Case("POST", "/admin/roles", null, false, "建角色（平台级）"),

                // ── 拒绝：本应用**未登记**的面板端点（与 kb-ops 的关键差异）──
                new Case("GET", "/admin/mappings", null, false, "账号映射 —— infra 未挂该面板，未登记即拒"),
                new Case("GET", "/admin/permissions", "client=" + APP, false, "权限点树 —— 未登记即拒"),
                new Case("GET", "/admin/roles/5/permission-codes", null, false, "角色→权限点 —— 未登记即拒"),

                // ── 拒绝：非管理面 / 内网通道 / 方法不匹配 ──
                new Case("PUT", "/internal/clients/" + APP + "/menus", null, false, "内网上报通道永不放行"),
                new Case("PUT", "/internal/clients/" + APP + "/accounts", null, false, "内网上报通道永不放行"),
                new Case("POST", "/admin/authz/migrate", null, false, "授权策略运维"),
                new Case("GET", "/admin/authorization-matrix", null, false, "跨应用授权矩阵"),
                new Case("DELETE", "/admin/users/302/client-roles", null, false, "角色端点不支持 DELETE"),
                new Case("POST", "/admin/roles/5/permission-codes", null, false, "权限点绑定不支持 POST"),
        };

        for (Case c : cases) {
            boolean actual = wl.isAllowed(c.method(), c.path(), c.query(), null, APP);
            boolean ok = actual == c.expected();
            if (ok) {
                pass++;
            } else {
                fail++;
            }
            System.out.printf("  %s %-6s %-46s client=%-22s -> %-5s  (%s)%n",
                    ok ? "[OK]" : "[NG]",
                    c.method(),
                    c.path(),
                    c.query() == null ? "-" : c.query(),
                    actual ? "ALLOW" : "DENY",
                    c.note());
        }

        System.out.println();
        System.out.println("=".repeat(76));
        System.out.printf("infra-monitor 白名单迁移等价性：%d 通过 / %d 失败（共 %d 项）%n", pass, fail, pass + fail);
        System.out.println("=".repeat(76));
        if (fail > 0) {
            System.exit(1);
        }
    }
}
