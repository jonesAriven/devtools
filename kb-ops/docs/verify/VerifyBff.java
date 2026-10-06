import com.marschat.auth.bff.BffWhitelist;
import com.marschat.auth.bff.BffWhitelistLoader;

/**
 * kb-ops 白名单迁移等价性验证 —— 用**已发布的 auth-core 2.2.0** 加载 kb-ops 真实
 * `bff-whitelist.yml`，逐条比对旧 `AdminProxyController.isAllowed()` 的判定结果。
 *
 * 目的：证明「266 行手写 Java 白名单」→「一份 yml」的迁移是**语义等价**（或更严）的，
 * 而不是靠肉眼读配置「看着对」。
 */
public class VerifyBff {

    private static final String APP = "marschat-kbops";
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
                // ── 允许：本应用成员/角色/菜单/映射/菜单授权 ──
                new Case("GET", "/admin/users", "client=" + APP, true, "成员列表（本应用作用域）"),
                new Case("GET", "/admin/users", null, true, "成员列表（缺省 client 视为放行，与旧逻辑一致）"),
                new Case("GET", "/admin/users/302/client-roles", "client=" + APP, true, "读本系统角色"),
                new Case("PUT", "/admin/users/302/client-roles", "client=" + APP, true, "写本系统角色"),
                new Case("GET", "/admin/users/302/menu-overrides", "client=" + APP, true, "读菜单减法"),
                new Case("PUT", "/admin/users/302/menu-overrides", "client=" + APP, true, "写菜单减法"),
                new Case("GET", "/admin/roles", null, true, "角色定义只读"),
                new Case("GET", "/admin/mappings", null, true, "账号映射（平台管理员）"),
                new Case("PUT", "/admin/mappings", null, true, "账号映射写"),
                new Case("GET", "/admin/mappings/302", null, true, "账号映射子路径"),
                new Case("GET", "/admin/permissions", "client=" + APP, true, "权限点树"),
                new Case("GET", "/admin/roles/5/permission-codes", null, true, "读角色→权限点"),
                new Case("PUT", "/admin/roles/5/permission-codes", null, true, "写角色→权限点"),
                new Case("GET", "/admin/clients/" + APP + "/members", null, true, "path 化成员列表"),
                new Case("POST", "/admin/clients/" + APP + "/members", null, true, "path 化加人"),
                new Case("DELETE", "/admin/clients/" + APP + "/members/302", null, true, "path 化移出"),

                // ── 拒绝：越界 client ──
                new Case("GET", "/admin/users", "client=marschat-other", false, "查询参数越界"),
                new Case("GET", "/admin/users", "client=" + APP + "&client=marschat-other", false, "HPP 参数污染"),
                new Case("GET", "/admin/users/302/client-roles", "client=other", false, "角色端点越界"),
                new Case("GET", "/admin/permissions", "client=other", false, "权限点越界"),
                new Case("GET", "/admin/clients/marschat-other/members", null, false, "路径段越界"),

                // ── 拒绝：平台级写端点（应用台不得下发）──
                new Case("POST", "/admin/users", null, false, "建身份"),
                new Case("PUT", "/admin/users/302", null, false, "改身份"),
                new Case("DELETE", "/admin/users/302", null, false, "删身份"),
                new Case("PUT", "/admin/users/302/password", null, false, "重置口令"),
                new Case("POST", "/admin/roles", null, false, "建角色（平台级）"),
                new Case("POST", "/admin/roles/5/permission-codes", null, false, "角色→权限点只允许 GET|PUT"),
                new Case("DELETE", "/admin/roles/5/permission-codes", null, false, "同上"),

                // ── 拒绝：非管理面 / 内网通道 ──
                new Case("PUT", "/internal/clients/" + APP + "/menus", null, false, "内网上报通道永不放行"),
                new Case("PUT", "/internal/clients/" + APP + "/accounts", null, false, "内网上报通道永不放行"),
                new Case("POST", "/admin/authz/migrate", null, false, "授权策略运维"),
                new Case("GET", "/admin/authorization-matrix", null, false, "跨应用授权矩阵"),

                // ── 拒绝：方法不匹配 ──
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
            System.out.printf("  %s %-6s %-46s client=%-24s → %-5s  (%s)%n",
                    ok ? "✓" : "✗",
                    c.method(),
                    c.path(),
                    c.query() == null ? "-" : c.query(),
                    actual ? "ALLOW" : "DENY",
                    c.note());
        }

        System.out.println();
        System.out.println("=".repeat(72));
        System.out.printf("kb-ops 白名单迁移等价性：%d 通过 / %d 失败（共 %d 项）%n", pass, fail, pass + fail);
        System.out.println("=".repeat(72));
        if (fail > 0) {
            System.exit(1);
        }
    }
}
