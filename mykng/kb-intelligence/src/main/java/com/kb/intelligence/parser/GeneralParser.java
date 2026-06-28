package com.kb.intelligence.parser;

import cn.hutool.crypto.digest.DigestUtil;
import com.kb.intelligence.entity.KnCommand;
import com.kb.intelligence.entity.KnDoc;
import com.kb.intelligence.entity.KnHost;
import com.kb.intelligence.mongo.doc.KnContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeneralParser implements DocParser {

    private final MarkdownParser markdownParser;
    private final ContentCleaner contentCleaner;
    private final CommandExtractor commandExtractor;

    private static final Pattern IP_PATTERN = Pattern.compile(
            "(?:(?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:192\\.168\\.\\d{1,3}\\.\\d{1,3})|(?:172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})|(?:100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}))"
    );
    private static final Pattern TAILSCALE_IP = Pattern.compile("100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern TAGS_PATTERN = Pattern.compile("#[\\u4e00-\\u9fa5a-zA-Z0-9_-]+");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public boolean supports(DocType docType) {
        return true;
    }

    @Override
    public ParseResult parse(String filePath, String fileName, String content, ParseResult result) {
        String baseName = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;

        KnDoc docMeta = result.getDocMeta();
        if (docMeta == null) {
            docMeta = new KnDoc();
            result.setDocMeta(docMeta);
        }

        docMeta.setTitle(extractTitle(baseName, content));
        docMeta.setFilePath(filePath);
        docMeta.setSourceId(generateSourceId(filePath));
        docMeta.setContentHash(DigestUtil.sha256Hex(content));
        docMeta.setCategory(extractCategory(filePath));
        docMeta.setTags(extractTags(content, baseName));
        docMeta.setSummary(extractSummary(content));
        docMeta.setStatus(1);
        docMeta.setWordCount(content.length());
        docMeta.setCreatedAt(LocalDateTime.now());
        docMeta.setUpdatedAt(LocalDateTime.now());

        List<KnContent.Section> sections = markdownParser.parseSections(content);
        KnContent knContent = new KnContent();
        knContent.setSections(sections);
        knContent.setPlainText(contentCleaner.extractPlainText(content));
        knContent.setWordCount(content.length());
        result.setContent(knContent);

        List<MarkdownParser.CommandBlock> blocks = markdownParser.extractCodeBlocks(content);
        List<KnCommand> commands = commandExtractor.extractCommands(null, blocks);
        result.getCommands().addAll(commands);

        extractInlineHosts(content, result);

        docMeta.setSectionCount(sections.size());
        docMeta.setCommandCount(commands.size());
        docMeta.setEntityCount(result.getHosts().size() + result.getServices().size());

        return result;
    }

    String extractTitle(String fileName, String content) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim().replaceAll("\\*+", "");
            }
        }
        return fileName;
    }

    String extractCategory(String filePath) {
        String normalized = filePath.replace("\\", "/").toLowerCase();
        if (normalized.contains("/ops/") || normalized.contains("运维") || normalized.contains("部署")) return "运维";
        if (normalized.contains("/dev/") || normalized.contains("开发")) return "开发";
        if (normalized.contains("/domain/") || normalized.contains("域名")) return "域名";
        if (normalized.contains("/network/") || normalized.contains("网络")) return "网络";
        if (normalized.contains("/plan/") || normalized.contains("方案")) return "方案";
        if (normalized.contains("/kb/") || normalized.contains("知识库")) return "知识库";
        if (normalized.contains("/project/") || normalized.contains("项目")) return "项目";
        if (normalized.contains("/memory/") || normalized.contains("记忆")) return "记忆";
        if (normalized.contains("openclaw")) return "OpenClaw";
        return "其他";
    }

    String extractTags(String content, String fileName) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher m = TAGS_PATTERN.matcher(content);
        while (m.find()) {
            tags.add(m.group());
        }
        String lower = content.toLowerCase();
        if (lower.contains("docker") || lower.contains("容器")) tags.add("#Docker");
        if (lower.contains("nginx")) tags.add("#Nginx");
        if (lower.contains("mysql")) tags.add("#MySQL");
        if (lower.contains("redis")) tags.add("#Redis");
        if (lower.contains("mongodb") || lower.contains("mongo")) tags.add("#MongoDB");
        if (lower.contains("meilisearch")) tags.add("#MeiliSearch");
        if (lower.contains("kubernetes") || lower.contains("k8s")) tags.add("#K8s");
        if (lower.contains("frp") || lower.contains("内网穿透")) tags.add("#FRP");
        if (lower.contains("tailscale")) tags.add("#Tailscale");
        if (lower.contains("nexus") || lower.contains("maven")) tags.add("#Nexus");
        if (lower.contains("minio")) tags.add("#MinIO");
        return String.join(",", tags);
    }

    String extractSummary(String content) {
        String[] lines = content.split("\\n");
        StringBuilder summary = new StringBuilder();
        boolean foundFirst = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) continue;
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("```")) { foundFirst = false; continue; }
            if (trimmed.startsWith("|")) continue;
            String clean = trimmed.replaceAll("[*#>`_\\-]", "").trim();
            if (clean.length() < 5) continue;
            summary.append(clean).append(" ");
            if (summary.length() > 200) break;
        }
        String s = summary.toString().trim();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    String generateSourceId(String filePath) {
        File f = new File(filePath);
        String path = f.getAbsolutePath().replace("\\", "/");
        String marker = "openclaw-work-space/";
        int idx = path.indexOf(marker);
        if (idx >= 0) {
            return "openclaw://" + path.substring(idx + marker.length());
        }
        return "file://" + path;
    }

    private void extractInlineHosts(String content, ParseResult result) {
        Set<String> existingIps = new HashSet<>();
        for (KnHost h : result.getHosts()) existingIps.add(h.getIp());

        Matcher m = IP_PATTERN.matcher(content);
        while (m.find()) {
            String ip = m.group();
            if (!existingIps.contains(ip)) {
                existingIps.add(ip);
                KnHost host = new KnHost();
                host.setIp(ip);
                host.setName(ip);
                host.setSshPort(22);
                host.setStatus("running");
                if (TAILSCALE_IP.matcher(ip).matches()) {
                    host.setTailscaleIp(ip);
                    host.setRole("tailscale-node");
                }
                result.getHosts().add(host);
            }
        }
    }
}
