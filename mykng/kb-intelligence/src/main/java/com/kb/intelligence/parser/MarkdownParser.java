package com.kb.intelligence.parser;

import com.kb.intelligence.mongo.doc.KnContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    public List<KnContent.Section> parseSections(String content) {
        List<KnContent.Section> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(content);
        List<int[]> headingPositions = new ArrayList<>();
        List<String[]> headingMeta = new ArrayList<>();

        while (matcher.find()) {
            headingPositions.add(new int[]{matcher.start(), matcher.end()});
            headingMeta.add(new String[]{matcher.group(1), matcher.group(2).trim()});
        }

        if (headingPositions.isEmpty()) {
            KnContent.Section sec = new KnContent.Section();
            sec.setTitle("(无标题)");
            sec.setLevel(0);
            sec.setContent(content.trim());
            sec.setWordCount(countWords(content));
            sections.add(sec);
            return sections;
        }

        for (int i = 0; i < headingPositions.size(); i++) {
            int start = headingPositions.get(i)[1];
            int end = (i + 1 < headingPositions.size()) ? headingPositions.get(i + 1)[0] : content.length();
            String sectionContent = content.substring(start, end).trim();

            KnContent.Section sec = new KnContent.Section();
            sec.setTitle(headingMeta.get(i)[1]);
            sec.setLevel(headingMeta.get(i)[0].length());
            sec.setContent(sectionContent);
            sec.setWordCount(countWords(sectionContent));
            sections.add(sec);
        }
        return sections;
    }

    public List<CommandBlock> extractCodeBlocks(String content) {
        List<CommandBlock> blocks = new ArrayList<>();
        Pattern pattern = Pattern.compile("```(bash|shell|sh|cmd|powershell|docker|yaml|yml|json|conf|nginx)?\\s*\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            CommandBlock block = new CommandBlock();
            block.setLang(m.group(1) != null ? m.group(1) : "text");
            block.setCode(m.group(2).trim());
            blocks.add(block);
        }
        return blocks;
    }

    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.replaceAll("\\s+", " ").trim().length();
    }

    @lombok.Data
    public static class CommandBlock {
        private String lang;
        private String code;
    }
}
