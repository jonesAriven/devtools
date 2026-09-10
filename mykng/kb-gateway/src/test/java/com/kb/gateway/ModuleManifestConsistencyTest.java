package com.kb.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「注册表 ←→ 派生产物」一致性门禁（Phase 0.5）。
 *
 * <h2>它拦的是什么</h2>
 * 2026-09-10 的 Phase 0 复盘发现：模块信息散落在 4 处手写副本（{@code module-registry.yml}、
 * {@code ModuleHealthController.KNOWN_MODULES}、{@code application.yml} 路由、
 * {@code .woodpecker.yml} 注释），rename 时漏改任何一处都<b>不会报错</b>，只会表现为
 * "菜单静默消失"。这条测试把"漏改"从静默变成构建失败。
 *
 * <h2>核心判据：哈希</h2>
 * {@code module-manifest.json} 内嵌生成时 {@code module-registry.yml} 的 sha256。
 * 有人改了注册表却没跑生成器 → 哈希不匹配 → 本测试失败 → CI 红。
 * 这就是"改注册表必须跑生成器"这条纪律的强制手段。
 *
 * <h2>为什么不用 Assumptions 放过</h2>
 * 找不到注册表时<b>直接失败</b>，不做 assumeTrue 跳过。因为可跳过 = 可被静默绕过 = 白做。
 * 从仓库检出构建时它一定在；只有在"脱离源码树跑 jar"这种不存在的场景下才会找不到。
 *
 * <h2>本地修复方式</h2>
 * <pre>python3 mykng/gen-registry.py</pre>
 */
@DisplayName("模块注册表一致性门禁")
class ModuleManifestConsistencyTest {

    private static final String MANIFEST_RESOURCE = "module-manifest.json";
    private static final String REGISTRY_FILE_NAME = "module-registry.yml";
    /** 从构建目录向上寻找注册表的最大层数，覆盖 kb-gateway / kb-parent / 仓库根 三种工作目录 */
    private static final int MAX_ASCEND = 4;

    private static final Pattern LB_TARGET = Pattern.compile("uri:\\s*lb://([\\w.\\-]+)");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ================================================================ 1. 哈希门禁

    @Test
    @DisplayName("注册表哈希与 module-manifest.json 记录一致（改注册表必须跑生成器）")
    void manifestHashMatchesRegistry() throws Exception {
        Path registry = resolveRegistry();
        JsonNode manifest = loadManifest();

        String recorded = manifest.path("sourceSha256").asText("");
        String actual = sha256(registry);

        assertFalse(recorded.isBlank(),
                () -> "module-manifest.json 缺少 sourceSha256 字段，产物不完整。"
                        + "\n修复：python3 mykng/gen-registry.py");

        assertEquals(actual, recorded,
                () -> "\n模块注册表与派生产物不同步 —— 你改了 " + registry + " 但没重新生成。\n"
                        + "  registry 当前 sha256 = " + actual + "\n"
                        + "  manifest 记录值      = " + recorded + "\n"
                        + "修复：python3 mykng/gen-registry.py 然后提交产物\n"
                        + "（生成器会同时刷新 module-manifest.json 与 docs/generated/*）");
    }

    // ================================================================ 2. 期望集结构

    @Test
    @DisplayName("expectedModules 非空、无重复、含核心模块")
    void expectedModulesAreSane() throws Exception {
        JsonNode manifest = loadManifest();
        List<String> expected = asStringList(manifest.path("expectedModules"));

        assertFalse(expected.isEmpty(),
                "expectedModules 为空 —— 网关将无法进行「期望 vs 实际」比对。"
                        + "\n检查 module-registry.yml 是否有 nacos-registered: true 的模块。");

        assertEquals(expected.size(), new HashSet<>(expected).size(),
                "expectedModules 有重复项：" + expected);

        assertTrue(expected.contains("kb-gateway"),
                "expectedModules 缺少 kb-gateway，实际=" + expected);
        assertTrue(expected.contains("auth-center"),
                "expectedModules 缺少 auth-center（SSO 签发方，缺了会导致登录页/操作日志链路异常），实际=" + expected);
    }

    @Test
    @DisplayName("manifest 模块名无重复，且 nacosRegistered 与 expectedModules 双向一致")
    void moduleEntriesConsistent() throws Exception {
        JsonNode manifest = loadManifest();
        List<String> expected = asStringList(manifest.path("expectedModules"));

        JsonNode modules = manifest.path("modules");
        assertTrue(modules.isArray() && !modules.isEmpty(), "manifest.modules 为空或非数组");

        Set<String> seen = new HashSet<>();
        Set<String> derived = new LinkedHashSet<>();
        for (JsonNode m : modules) {
            String name = m.path("name").asText("");
            assertFalse(name.isBlank(), "manifest.modules 存在无名条目");
            assertTrue(seen.add(name), "manifest.modules 出现重复模块名：" + name);
            if (m.path("nacosRegistered").asBoolean(false)) {
                derived.add(name);
            }
        }

        assertEquals(new HashSet<>(expected), derived,
                "expectedModules 与 modules[].nacosRegistered=true 不一致。"
                        + "\n  expectedModules = " + expected
                        + "\n  由条目派生      = " + derived
                        + "\n修复：python3 mykng/gen-registry.py");
    }

    // ================================================================ 3. 路由一致性

    @Test
    @DisplayName("网关 application.yml 的 lb:// 目标必须全部在 expectedModules 内")
    void gatewayRouteTargetsAreDeclared() throws Exception {
        List<String> expected = asStringList(loadManifest().path("expectedModules"));
        String appYml = readClasspathText("application.yml");

        Set<String> targets = new LinkedHashSet<>();
        Matcher m = LB_TARGET.matcher(appYml);
        while (m.find()) {
            targets.add(m.group(1));
        }

        assertFalse(targets.isEmpty(),
                "未能从 application.yml 解析出任何 lb:// 路由目标，正则或配置结构可能已变更，"
                        + "本门禁会因失配而失去意义，必须修复测试而非忽略。");

        List<String> undeclared = new ArrayList<>();
        for (String t : targets) {
            if (!expected.contains(t)) {
                undeclared.add(t);
            }
        }
        assertTrue(undeclared.isEmpty(),
                "\n网关声明了到下列服务的路由，但它们不在注册表期望集内：\n  " + undeclared
                        + "\n  期望集 = " + expected
                        + "\n两种可能：①注册表漏声明该模块 → 去 module-registry.yml 补一条并设 nacos-registered: true；"
                        + "\n        ②路由写错了模块名（这正是 kb-auth→auth-center 事故的形态）→ 改 application.yml。"
                        + "\n改完记得跑：python3 mykng/gen-registry.py");
    }

    // ================================================================ 工具

    private static JsonNode loadManifest() throws Exception {
        ClassPathResource res = new ClassPathResource(MANIFEST_RESOURCE);
        assertTrue(res.exists(),
                "classpath 下找不到 " + MANIFEST_RESOURCE
                        + "\n说明生成器没跑、产物没提交，或构建资源过滤把它漏掉了。"
                        + "\n修复：python3 mykng/gen-registry.py");
        try (InputStream in = res.getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    private static String readClasspathText(String name) throws Exception {
        ClassPathResource res = new ClassPathResource(name);
        assertTrue(res.exists(), "classpath 下找不到 " + name);
        try (InputStream in = res.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 定位 {@code mykng/module-registry.yml}。
     * <p>优先取系统属性 {@code -Dmykng.registry.file}（CI 里由 build-mykng.sh 显式传入），
     * 否则从 {@code user.dir} 逐层向上探测，同时兼容"当前目录就是 mykng/""当前目录是仓库根"
     * 与"当前目录是某个子模块"三种情况。
     */
    private static Path resolveRegistry() {
        String explicit = System.getProperty("mykng.registry.file");
        if (explicit != null && !explicit.isBlank()) {
            Path p = Path.of(explicit).toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(p),
                    "-Dmykng.registry.file=" + explicit + " 指向的文件不存在。");
            return p;
        }

        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i <= MAX_ASCEND && dir != null; i++) {
            Path direct = dir.resolve(REGISTRY_FILE_NAME);
            if (Files.isRegularFile(direct)) {
                return direct;
            }
            Path nested = dir.resolve("mykng").resolve(REGISTRY_FILE_NAME);
            if (Files.isRegularFile(nested)) {
                return nested;
            }
            dir = dir.getParent();
        }

        throw new AssertionError(
                "找不到 " + REGISTRY_FILE_NAME + "（从 user.dir=" + System.getProperty("user.dir")
                        + " 向上探测 " + MAX_ASCEND + " 层未果）。\n"
                        + "该文件是模块信息的唯一真源，缺失时本门禁无意义，因此直接失败而非跳过。\n"
                        + "可用 -Dmykng.registry.file=<绝对路径> 显式指定。");
    }

    private static List<String> asStringList(JsonNode arrayNode) {
        assertNotNull(arrayNode, "字段缺失");
        assertTrue(arrayNode.isArray(), "字段不是数组：" + arrayNode);
        List<String> out = new ArrayList<>();
        arrayNode.forEach(n -> out.add(n.asText()));
        return out;
    }

    /**
     * 注册表内容摘要。
     *
     * <p><b>必须先把 CRLF 归一到 LF 再算</b>：仓库同时存在 Windows 工作区与 Linux CI 两侧。
     * 若直接对原始字节取哈希，同一份文件会因换行符不同产出两个哈希，这条门禁就退化成
     * "看你在哪台机器上跑"——在 Windows 上是绿的，推到 CI 就红，而且报错信息会指向
     * "你改了注册表"，完全误导。生成器侧 {@code registry_sha256()} 做了同样的归一。
     */
    private static String sha256(Path file) throws Exception {
        byte[] raw = Files.readAllBytes(file);
        String normalized = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16))
              .append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
