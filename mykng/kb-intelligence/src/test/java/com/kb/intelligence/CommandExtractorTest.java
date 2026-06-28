package com.kb.intelligence;

import com.kb.intelligence.entity.KnCommand;
import com.kb.intelligence.parser.CommandExtractor;
import com.kb.intelligence.parser.MarkdownParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CommandExtractor 单元测试")
class CommandExtractorTest {

    private CommandExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new CommandExtractor();
    }

    private MarkdownParser.CommandBlock block(String lang, String code) {
        MarkdownParser.CommandBlock block = new MarkdownParser.CommandBlock();
        block.setLang(lang);
        block.setCode(code);
        return block;
    }

    @Test
    @DisplayName("extractCommands - bash代码块提取命令")
    void extractCommands_bashBlock_extractsCommands() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "docker run -d nginx\nkubectl get pods")
        ));

        assertEquals(2, commands.size());
        assertEquals("docker run -d nginx", commands.get(0).getCommand());
        assertEquals("kubectl get pods", commands.get(1).getCommand());
        assertEquals(1L, commands.get(0).getDocId());
    }

    @Test
    @DisplayName("extractCommands - shell代码块提取命令")
    void extractCommands_shellBlock_extractsCommands() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("shell", "git push origin main\ncurl http://example.com")
        ));

        assertEquals(2, commands.size());
        assertEquals("git push origin main", commands.get(0).getCommand());
        assertEquals("curl http://example.com", commands.get(1).getCommand());
    }

    @Test
    @DisplayName("extractCommands - yaml代码块不提取命令")
    void extractCommands_yamlBlock_notExtracted() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("yaml", "service:\n  name: nginx")
        ));

        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("extractCommands - 空blocks列表返回空列表")
    void extractCommands_emptyBlocks_returnsEmptyList() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of());
        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("extractCommands - null语言默认为text不提取")
    void extractCommands_nullLang_notExtracted() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block(null, "docker run -d nginx")
        ));
        assertTrue(commands.isEmpty());
    }

    @Test
    @DisplayName("extractCommands - 危险命令标记为高风险")
    void extractCommands_dangerousCommand_highRisk() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "rm -rf /tmp/test")
        ));

        assertEquals(1, commands.size());
        assertEquals("high", commands.get(0).getRiskLevel());
    }

    @Test
    @DisplayName("extractCommands - chmod 777标记为高风险")
    void extractCommands_chmod777_highRisk() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "chmod 777 /etc/passwd")
        ));

        assertEquals(1, commands.size());
        assertEquals("high", commands.get(0).getRiskLevel());
    }

    @Test
    @DisplayName("extractCommands - kill命令标记为中等风险")
    void extractCommands_killCommand_mediumRisk() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "kill -9 1234")
        ));

        assertEquals(1, commands.size());
        assertEquals("medium", commands.get(0).getRiskLevel());
    }

    @Test
    @DisplayName("extractCommands - 普通命令标记为低风险")
    void extractCommands_normalCommand_lowRisk() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "docker run -d nginx")
        ));

        assertEquals(1, commands.size());
        assertEquals("low", commands.get(0).getRiskLevel());
    }

    @Test
    @DisplayName("extractCommands - docker命令分类为container")
    void extractCommands_dockerCommand_categorizedAsContainer() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "docker run -d nginx")
        ));

        assertEquals(1, commands.size());
        assertEquals("container", commands.get(0).getCategory());
    }

    @Test
    @DisplayName("extractCommands - kubectl命令分类为k8s")
    void extractCommands_kubectlCommand_categorizedAsK8s() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "kubectl get pods")
        ));

        assertEquals(1, commands.size());
        assertEquals("k8s", commands.get(0).getCategory());
    }

    @Test
    @DisplayName("extractCommands - git命令分类为git")
    void extractCommands_gitCommand_categorizedAsGit() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "git push origin main")
        ));

        assertEquals(1, commands.size());
        assertEquals("git", commands.get(0).getCategory());
    }

    @Test
    @DisplayName("extractCommands - curl命令分类为network")
    void extractCommands_curlCommand_categorizedAsNetwork() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "curl http://example.com")
        ));

        assertEquals(1, commands.size());
        assertEquals("network", commands.get(0).getCategory());
    }

    @Test
    @DisplayName("extractCommands - apt命令分类为package")
    void extractCommands_aptCommand_categorizedAsPackage() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "apt-get install nginx")
        ));

        assertEquals(1, commands.size());
        assertEquals("package", commands.get(0).getCategory());
    }

    @Test
    @DisplayName("extractCommands - powershell代码块标记osType为windows")
    void extractCommands_powershellBlock_osTypeWindows() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("powershell", "mkdir test")
        ));

        assertEquals(1, commands.size());
        assertEquals("windows", commands.get(0).getOsType());
    }

    @Test
    @DisplayName("extractCommands - bash代码块标记osType为linux")
    void extractCommands_bashBlock_osTypeLinux() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "docker run -d nginx")
        ));

        assertEquals(1, commands.size());
        assertEquals("linux", commands.get(0).getOsType());
    }

    @Test
    @DisplayName("extractCommands - 多行命令(反斜杠续行)合并为一条命令")
    void extractCommands_multiLineCommand_mergedAsOne() {
        String code = "docker run -d \\\n  --name nginx \\\n  nginx:latest";
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", code)
        ));

        assertEquals(1, commands.size());
        assertTrue(commands.get(0).getCommand().startsWith("docker run"));
        assertTrue(commands.get(0).getCommand().contains("nginx:latest"));
    }

    @Test
    @DisplayName("extractCommands - 注释行被跳过")
    void extractCommands_commentLines_skipped() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "# This is a comment\napt-get install nginx")
        ));

        assertEquals(1, commands.size());
        assertEquals("apt-get install nginx", commands.get(0).getCommand());
    }

    @Test
    @DisplayName("extractCommands - 命令行提示符($)被去除")
    void extractCommands_promptPrefix_removed() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "$ docker run -d nginx")
        ));

        assertEquals(1, commands.size());
        assertEquals("docker run -d nginx", commands.get(0).getCommand());
    }

    @Test
    @DisplayName("extractCommands - 空行分隔多条命令")
    void extractCommands_emptyLineSeparates_commands() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "docker run -d nginx\n\nkubectl get pods")
        ));

        assertEquals(2, commands.size());
        assertEquals("docker run -d nginx", commands.get(0).getCommand());
        assertEquals("kubectl get pods", commands.get(1).getCommand());
    }

    @Test
    @DisplayName("extractCommands - 多个代码块全部提取")
    void extractCommands_multipleBlocks_allExtracted() {
        List<KnCommand> commands = extractor.extractCommands(1L, List.of(
                block("bash", "docker run -d nginx"),
                block("shell", "git push origin main")
        ));

        assertEquals(2, commands.size());
    }
}
