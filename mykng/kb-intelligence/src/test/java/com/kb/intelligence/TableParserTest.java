package com.kb.intelligence;

import com.kb.intelligence.entity.KnHost;
import com.kb.intelligence.entity.KnPort;
import com.kb.intelligence.entity.KnService;
import com.kb.intelligence.entity.KnCredential;
import com.kb.intelligence.entity.KnDoc;
import com.kb.intelligence.parser.DocType;
import com.kb.intelligence.parser.ParseResult;
import com.kb.intelligence.parser.TableParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TableParser 单元测试")
class TableParserTest {

    private TableParser tableParser;

    @BeforeEach
    void setUp() {
        tableParser = new TableParser();
    }

    private ParseResult newResult() {
        ParseResult result = new ParseResult();
        KnDoc docMeta = new KnDoc();
        result.setDocMeta(docMeta);
        return result;
    }

    @Test
    @DisplayName("supports - TABLE类型应返回true")
    void supports_tableType_returnsTrue() {
        assertTrue(tableParser.supports(DocType.TABLE));
    }

    @Test
    @DisplayName("supports - 非TABLE类型应返回false")
    void supports_nonTableType_returnsFalse() {
        assertFalse(tableParser.supports(DocType.PLAN));
        assertFalse(tableParser.supports(DocType.GENERAL));
    }

    @Test
    @DisplayName("parse - 正常IP提取主机")
    void parse_normalTable_extractsHost() {
        String content = """
            # 主机清单

            | 主机名 | IP | 用户名 | 密码 | 角色 |
            |--------|-----|--------|------|------|
            | web-01 | 192.168.1.10 | admin | pass123 | web-server |
            """;

        ParseResult result = tableParser.parse("/docs/hosts.md", "hosts.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        KnHost host = result.getHosts().get(0);
        assertEquals("192.168.1.10", host.getIp());
        assertEquals("web-01", host.getName());
        assertEquals("admin", host.getUsername());
        assertEquals("pass123", host.getPasswordEncrypted());
        assertEquals("web-server", host.getRole());
        assertEquals(22, host.getSshPort());
        assertEquals("running", host.getStatus());
    }

    @Test
    @DisplayName("parse - 无IP的表格不提取主机")
    void parse_tableWithoutIp_noHostExtracted() {
        String content = """
            # 文档

            | 主机名 | 位置 | 说明 |
            |--------|------|------|
            | web-01 | 机房A | 主节点 |
            """;

        ParseResult result = tableParser.parse("/docs/no-ip.md", "no-ip.md", content, newResult());

        assertTrue(result.getHosts().isEmpty());
    }

    @Test
    @DisplayName("parse - 多字段提取主机完整信息")
    void parse_multiFieldTable_extractsAllFields() {
        String content = """
            | 主机名 | IP | Tailscale IP | 用户名 | 密码 | 角色 | 系统 | 备注 |
            |--------|-----|-------------|--------|------|------|------|------|
            | web-01 | 192.168.1.10 | 100.64.0.1 | admin | pass123 | web-server | Ubuntu 22.04 | 主WEB节点 |
            """;

        ParseResult result = tableParser.parse("/docs/full.md", "full.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        KnHost host = result.getHosts().get(0);
        assertEquals("192.168.1.10", host.getIp());
        assertEquals("web-01", host.getName());
        assertEquals("100.64.0.1", host.getTailscaleIp());
        assertEquals("admin", host.getUsername());
        assertEquals("pass123", host.getPasswordEncrypted());
        assertEquals("web-server", host.getRole());
        assertEquals("Ubuntu 22.04", host.getOsType());
        assertEquals("主WEB节点", host.getRemark());
    }

    @Test
    @DisplayName("parse - 公网IP放入备注字段")
    void parse_publicIp_putsInRemark() {
        String content = """
            | 主机名 | IP | 公网IP |
            |--------|-----|--------|
            | web-01 | 192.168.1.10 | 203.0.113.1 |
            """;

        ParseResult result = tableParser.parse("/docs/public.md", "public.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        KnHost host = result.getHosts().get(0);
        assertEquals("192.168.1.10", host.getIp());
        assertNotNull(host.getRemark());
        assertTrue(host.getRemark().contains("203.0.113.1"));
    }

    @Test
    @DisplayName("parse - 表格中包含服务列时提取服务")
    void parse_tableWithService_extractsService() {
        String content = """
            | 主机名 | IP | 服务 | 版本 |
            |--------|-----|------|------|
            | web-01 | 192.168.1.10 | nginx | 1.24.0 |
            """;

        ParseResult result = tableParser.parse("/docs/svc.md", "svc.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        assertEquals(1, result.getServices().size());
        KnService svc = result.getServices().get(0);
        assertEquals("nginx", svc.getName());
        assertEquals("1.24.0", svc.getVersion());
        assertEquals("running", svc.getStatus());
    }

    @Test
    @DisplayName("parse - 表格中无服务列时不提取服务")
    void parse_tableWithoutService_noServiceExtracted() {
        String content = """
            | 主机名 | IP |
            |--------|-----|
            | web-01 | 192.168.1.10 |
            """;

        ParseResult result = tableParser.parse("/docs/no-svc.md", "no-svc.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        assertTrue(result.getServices().isEmpty());
    }

    @Test
    @DisplayName("parse - 包含端口信息的表格提取端口")
    void parse_tableWithPortInfo_extractsPort() {
        String content = """
            | 主机名 | IP | 备注 |
            |--------|-----|------|
            | web-01 | 192.168.1.10 | ssh:22 |
            """;

        ParseResult result = tableParser.parse("/docs/port.md", "port.md", content, newResult());

        assertFalse(result.getPorts().isEmpty());
        KnPort port = result.getPorts().get(0);
        assertEquals(22, port.getPort());
        assertEquals("tcp", port.getProtocol());
        assertEquals(0, port.getExposed());
    }

    @Test
    @DisplayName("parse - 无端口信息的表格不提取端口")
    void parse_tableWithoutPort_noPortExtracted() {
        String content = """
            | 主机名 | IP |
            |--------|-----|
            | web-01 | 192.168.1.10 |
            """;

        ParseResult result = tableParser.parse("/docs/no-port.md", "no-port.md", content, newResult());

        assertTrue(result.getPorts().isEmpty());
    }

    @Test
    @DisplayName("parse - 包含用户名密码时提取凭据")
    void parse_tableWithCredentials_extractsCredential() {
        String content = """
            | 主机名 | IP | 用户名 | 密码 |
            |--------|-----|--------|------|
            | web-01 | 192.168.1.10 | admin | pass123 |
            """;

        ParseResult result = tableParser.parse("/docs/cred.md", "cred.md", content, newResult());

        assertEquals(1, result.getCredentials().size());
        KnCredential cred = result.getCredentials().get(0);
        assertEquals("admin", cred.getUsername());
        assertEquals("pass123", cred.getPasswordEncrypted());
        assertEquals("ssh", cred.getCredType());
    }

    @Test
    @DisplayName("parse - 非表格行中的IP提取为内联主机")
    void parse_inlineHostInText_extractsHost() {
        String content = """
            # 文档

            部署到 192.168.1.20 这台机器上。
            """;

        ParseResult result = tableParser.parse("/docs/inline.md", "inline.md", content, newResult());

        assertFalse(result.getHosts().isEmpty());
        KnHost host = result.getHosts().get(0);
        assertEquals("192.168.1.20", host.getIp());
    }

    @Test
    @DisplayName("parse - 多行表格提取多个主机")
    void parse_multiRowTable_extractsMultipleHosts() {
        String content = """
            | 主机名 | IP |
            |--------|-----|
            | web-01 | 192.168.1.10 |
            | web-02 | 192.168.1.11 |
            | db-01  | 192.168.1.20 |
            """;

        ParseResult result = tableParser.parse("/docs/multi.md", "multi.md", content, newResult());

        assertEquals(3, result.getHosts().size());
        assertEquals("192.168.1.10", result.getHosts().get(0).getIp());
        assertEquals("192.168.1.11", result.getHosts().get(1).getIp());
        assertEquals("192.168.1.20", result.getHosts().get(2).getIp());
    }

    @Test
    @DisplayName("parse - 10.x网段IP可正常提取")
    void parse_tenNetworkIp_extractsHost() {
        String content = """
            | 主机名 | IP |
            |--------|-----|
            | node-01 | 10.0.1.5 |
            """;

        ParseResult result = tableParser.parse("/docs/ten.md", "ten.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        assertEquals("10.0.1.5", result.getHosts().get(0).getIp());
    }

    @Test
    @DisplayName("parse - IP在值中而非IP列时可从值中提取")
    void parse_ipInValue_extractsHost() {
        String content = """
            | 名称 | 地址 |
            |------|------|
            | node-01 | 172.16.0.5 |
            """;

        ParseResult result = tableParser.parse("/docs/val.md", "val.md", content, newResult());

        assertEquals(1, result.getHosts().size());
        KnHost host = result.getHosts().get(0);
        assertEquals("172.16.0.5", host.getIp());
    }
}
