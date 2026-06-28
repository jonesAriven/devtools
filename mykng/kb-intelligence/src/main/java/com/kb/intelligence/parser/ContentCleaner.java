package com.kb.intelligence.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ContentCleaner {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\([^)]+\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\([^)]+\\)");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    public String clean(String raw) {
        if (raw == null) return "";
        String text = IMAGE_PATTERN.matcher(raw).replaceAll("");
        text = LINK_PATTERN.matcher(text).replaceAll("$1");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        text = text.replace("**", "").replace("__", "")
                   .replace("*", "").replace("_", "")
                   .replace("`", "").replace("~~", "");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    public String extractPlainText(String raw) {
        if (raw == null) return "";
        String withoutCodeBlocks = raw.replaceAll("(?s)```.*?```", "");
        String cleaned = clean(withoutCodeBlocks);
        StringBuilder sb = new StringBuilder();
        String[] lines = cleaned.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) continue;
            if (trimmed.startsWith("#")) {
                sb.append(trimmed.replaceAll("^#+\\s*", "")).append("\n");
            } else if (trimmed.startsWith("|")) {
                sb.append(trimmed).append("\n");
            } else {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
