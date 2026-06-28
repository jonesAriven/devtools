package com.kb.intelligence;

import com.kb.intelligence.parser.ContentCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentCleaner 单元测试")
class ContentCleanerTest {

    private ContentCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new ContentCleaner();
    }

    @Test
    @DisplayName("clean - null输入返回空字符串")
    void clean_nullInput_returnsEmptyString() {
        assertEquals("", cleaner.clean(null));
    }

    @Test
    @DisplayName("clean - 去除Markdown加粗标记")
    void clean_boldMarkdown_removesAsterisks() {
        String input = "这是**加粗**文本";
        String result = cleaner.clean(input);
        assertEquals("这是加粗文本", result);
    }

    @Test
    @DisplayName("clean - 去除Markdown斜体标记")
    void clean_italicMarkdown_removesUnderscores() {
        String input = "这是_斜体_文本";
        String result = cleaner.clean(input);
        assertEquals("这是斜体文本", result);
    }

    @Test
    @DisplayName("clean - 去除行内代码标记")
    void clean_inlineCode_removesBackticks() {
        String input = "运行 `npm install` 安装依赖";
        String result = cleaner.clean(input);
        assertEquals("运行 npm install 安装依赖", result);
    }

    @Test
    @DisplayName("clean - 去除删除线标记")
    void clean_strikethrough_removesTildes() {
        String input = "这是~~删除~~文本";
        String result = cleaner.clean(input);
        assertEquals("这是删除文本", result);
    }

    @Test
    @DisplayName("clean - 去除HTML标签")
    void clean_htmlTags_removesTags() {
        String input = "<div>内容</div><br/><p>段落</p>";
        String result = cleaner.clean(input);
        assertEquals("内容段落", result);
    }

    @Test
    @DisplayName("clean - 去除图片语法")
    void clean_imageSyntax_removesImage() {
        String input = "前文![alt text](http://example.com/image.png)后文";
        String result = cleaner.clean(input);
        assertEquals("前文后文", result);
    }

    @Test
    @DisplayName("clean - 链接保留文本去除URL")
    void clean_linkSyntax_keepsTextRemovesUrl() {
        String input = "访问[官网](http://example.com)了解更多";
        String result = cleaner.clean(input);
        assertEquals("访问官网了解更多", result);
    }

    @Test
    @DisplayName("clean - 多余空行压缩为两个换行")
    void clean_multipleNewlines_compressesToTwo() {
        String input = "第一行\n\n\n\n\n第二行";
        String result = cleaner.clean(input);
        assertEquals("第一行\n\n第二行", result);
    }

    @Test
    @DisplayName("clean - 综合Markdown语法清理")
    void clean_complexMarkdown_removesAllSyntax() {
        String input = """
            # 标题

            **加粗** 和 _斜体_ 和 `代码`

            <div>HTML</div>

            [链接](http://example.com)
            """;
        String result = cleaner.clean(input);
        assertFalse(result.contains("**"));
        assertFalse(result.contains("_"));
        assertFalse(result.contains("`"));
        assertFalse(result.contains("<div>"));
        assertFalse(result.contains("[链接]"));
        assertTrue(result.contains("加粗"));
        assertTrue(result.contains("链接"));
    }

    @Test
    @DisplayName("clean - 结果去除首尾空白")
    void clean_resultIsTrimmed() {
        String input = "\n\n  内容  \n\n";
        String result = cleaner.clean(input);
        assertEquals("内容", result);
    }

    @Test
    @DisplayName("extractPlainText - 提取标题并去除#前缀")
    void extractPlainText_heading_removesHashPrefix() {
        String input = "## 二级标题\n\n正文内容";
        String result = cleaner.extractPlainText(input);
        assertTrue(result.contains("二级标题"));
        assertFalse(result.contains("##"));
        assertTrue(result.contains("正文内容"));
    }

    @Test
    @DisplayName("extractPlainText - 代码块被跳过")
    void extractPlainText_codeBlock_skipped() {
        String input = "正文\n\n```bash\ndocker run\n```\n\n后续";
        String result = cleaner.extractPlainText(input);
        assertFalse(result.contains("docker run"));
        assertTrue(result.contains("正文"));
        assertTrue(result.contains("后续"));
    }

    @Test
    @DisplayName("extractPlainText - 表格行保留")
    void extractPlainText_tableRows_preserved() {
        String input = "| 主机 | IP |\n|------|----|\n| web | 1.1.1.1 |";
        String result = cleaner.extractPlainText(input);
        assertTrue(result.contains("| 主机 | IP |"));
    }

    @Test
    @DisplayName("extractPlainText - null输入返回空字符串")
    void extractPlainText_nullInput_returnsEmptyString() {
        assertEquals("", cleaner.extractPlainText(null));
    }
}
