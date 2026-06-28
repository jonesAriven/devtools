package com.kb.intelligence;

import com.kb.intelligence.parser.DocType;
import com.kb.intelligence.parser.DocTypeDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocTypeDetector 单元测试")
class DocTypeDetectorTest {

    private DocTypeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DocTypeDetector();
    }

    @Test
    @DisplayName("detect - 主机清单文件名返回TABLE")
    void detect_hostListFileName_returnsTable() {
        assertEquals(DocType.TABLE, detector.detect("主机清单.md", "# 主机清单"));
    }

    @Test
    @DisplayName("detect - 服务器列表文件名返回TABLE")
    void detect_serverListFileName_returnsTable() {
        assertEquals(DocType.TABLE, detector.detect("服务器列表.md", "内容"));
    }

    @Test
    @DisplayName("detect - host相关文件名返回TABLE")
    void detect_hostFileName_returnsTable() {
        assertEquals(DocType.TABLE, detector.detect("host-list.md", "hosts"));
        assertEquals(DocType.TABLE, detector.detect("server-inventory.md", "servers"));
    }

    @Test
    @DisplayName("detect - 部署方案文件名返回PLAN")
    void detect_deployFileName_returnsPlan() {
        assertEquals(DocType.PLAN, detector.detect("部署方案.md", "部署内容"));
    }

    @Test
    @DisplayName("detect - 内容含bash代码块返回PLAN")
    void detect_bashCodeContent_returnsPlan() {
        String content = "## 命令\n\n```bash\ndocker run -d nginx\n```";
        assertEquals(DocType.PLAN, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 内容含docker关键字返回PLAN")
    void detect_dockerContent_returnsPlan() {
        String content = "使用 docker 部署应用";
        assertEquals(DocType.PLAN, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 故障报告文件名返回TIMELINE")
    void detect_incidentFileName_returnsTimeline() {
        assertEquals(DocType.TIMELINE, detector.detect("故障报告.md", "故障内容"));
    }

    @Test
    @DisplayName("detect - 踩坑文件名返回TIMELINE")
    void detect_pitfallFileName_returnsTimeline() {
        assertEquals(DocType.TIMELINE, detector.detect("踩坑记录.md", "踩坑内容"));
    }

    @Test
    @DisplayName("detect - 内容含## 问题返回TIMELINE")
    void detect_issueContent_returnsTimeline() {
        String content = "# 日志\n\n## 问题\n\n系统出现异常";
        assertEquals(DocType.TIMELINE, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 内容含## 故障返回TIMELINE")
    void detect_failureContent_returnsTimeline() {
        String content = "# 文档\n\n## 故障\n\n系统宕机";
        assertEquals(DocType.TIMELINE, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 内容含树形结构返回GRAPH")
    void detect_treeContent_returnsGraph() {
        String content = "项目结构：\n\n├── src\n│   ├── main\n└── test";
        assertEquals(DocType.GRAPH, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 内容含mermaid返回GRAPH")
    void detect_mermaidContent_returnsGraph() {
        String content = "```mermaid\ngraph TD\nA --> B\n```";
        assertEquals(DocType.GRAPH, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 开发规范文件名返回RULE")
    void detect_conventionFileName_returnsRule() {
        assertEquals(DocType.RULE, detector.detect("开发规范.md", "规范内容"));
    }

    @Test
    @DisplayName("detect - guide文件名返回RULE")
    void detect_guideFileName_returnsRule() {
        assertEquals(DocType.RULE, detector.detect("style-guide.md", "guide content"));
    }

    @Test
    @DisplayName("detect - 普通Markdown返回GENERAL")
    void detect_normalMarkdown_returnsGeneral() {
        String content = "# Hello World\n\nThis is a test document.";
        assertEquals(DocType.GENERAL, detector.detect("notes.md", content));
    }

    @Test
    @DisplayName("detect - 空文件名和空内容返回GENERAL")
    void detect_emptyFileNameAndContent_returnsGeneral() {
        assertEquals(DocType.GENERAL, detector.detect("", ""));
    }

    @Test
    @DisplayName("detect - 速查文件名返回TABLE")
    void detect_cheatsheetFileName_returnsTable() {
        assertEquals(DocType.TABLE, detector.detect("速查表.md", "速查内容"));
    }

    @Test
    @DisplayName("detect - 账密文件名返回TABLE")
    void detect_credentialsFileName_returnsTable() {
        assertEquals(DocType.TABLE, detector.detect("账密清单.md", "账号密码"));
    }
}
