package com.kb.intelligence.parser;

import com.kb.intelligence.entity.KnTimeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimelineParser implements DocParser {

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2}日?(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?)|(\\d{1,2}月\\d{1,2}日)"
    );

    private static final Pattern ISSUE_PATTERN = Pattern.compile(
            "(?:问题|故障|错误|报错|异常|坑|issue|error|bug|fail(?:ure|ed)?)[：:]*\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SOLUTION_PATTERN = Pattern.compile(
            "(?:解决|方案|修复|处理|solve|fix|resolv)[：:]*\\s*(.+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HEADING_EVENT_PATTERN = Pattern.compile(
            "^(#{2,3})\\s+(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[^\\n]*)",
            Pattern.MULTILINE
    );

    @Override
    public boolean supports(DocType docType) {
        return docType == DocType.TIMELINE;
    }

    @Override
    public ParseResult parse(String filePath, String fileName, String content, ParseResult result) {
        log.debug("TimelineParser 解析: {}", fileName);

        parseByHeadings(content, result);
        parseByKeywords(content, result);

        return result;
    }

    private void parseByHeadings(String content, ParseResult result) {
        Matcher m = HEADING_EVENT_PATTERN.matcher(content);
        while (m.find()) {
            String heading = m.group(2).trim();
            int eventLevel = m.group(1).length();

            int start = m.end();
            int nextHeading = content.length();
            Matcher nextM = HEADING_EVENT_PATTERN.matcher(content);
            nextM.region(start, content.length());
            if (nextM.find()) nextHeading = nextM.start();

            String body = content.substring(start, nextHeading).trim();
            KnTimeline event = buildEvent(heading, body);
            result.getTimelines().add(event);
        }
    }

    private void parseByKeywords(String content, ParseResult result) {
        String[] lines = content.split("\\n");
        StringBuilder currentIssue = new StringBuilder();
        StringBuilder currentSolution = new StringBuilder();
        String currentDate = null;
        boolean inIssue = false;
        boolean inSolution = false;

        for (String line : lines) {
            String trimmed = line.trim();

            Matcher dm = DATE_PATTERN.matcher(trimmed);
            if (dm.find()) {
                flushEvent(currentDate, currentIssue, currentSolution, result);
                currentDate = normalizeDate(dm.group(1) != null ? dm.group(1) : dm.group(2));
                currentIssue.setLength(0);
                currentSolution.setLength(0);
                inIssue = false;
                inSolution = false;
            }

            if (trimmed.startsWith("#")) continue;

            Matcher im = ISSUE_PATTERN.matcher(trimmed);
            if (im.find()) {
                flushEvent(currentDate, currentIssue, currentSolution, result);
                currentIssue.setLength(0);
                currentIssue.append(im.group(1).trim());
                inIssue = true;
                inSolution = false;
                continue;
            }

            Matcher sm = SOLUTION_PATTERN.matcher(trimmed);
            if (sm.find()) {
                currentSolution.setLength(0);
                currentSolution.append(sm.group(1).trim());
                inSolution = true;
                inIssue = false;
                continue;
            }

            if (inIssue && !trimmed.isEmpty() && !trimmed.startsWith("```")) {
                currentIssue.append(" ").append(trimmed);
            } else if (inSolution && !trimmed.isEmpty() && !trimmed.startsWith("```")) {
                currentSolution.append(" ").append(trimmed);
            }
        }
        flushEvent(currentDate, currentIssue, currentSolution, result);
    }

    private void flushEvent(String date, StringBuilder issue, StringBuilder solution, ParseResult result) {
        String issueStr = issue.toString().trim();
        if (issueStr.isEmpty()) return;

        KnTimeline event = new KnTimeline();
        event.setEventTime(date);
        event.setTitle(issueStr.length() > 50 ? issueStr.substring(0, 50) + "..." : issueStr);
        event.setDescription(issueStr);
        event.setSolution(solution.toString().trim());
        event.setEventType("issue");
        event.setSeverity(assessSeverity(issueStr));
        event.setStatus(solution.length() > 0 ? "resolved" : "open");
        result.getTimelines().add(event);
    }

    private KnTimeline buildEvent(String heading, String body) {
        KnTimeline event = new KnTimeline();
        Matcher dm = DATE_PATTERN.matcher(heading);
        if (dm.find()) {
            event.setEventTime(normalizeDate(dm.group(1) != null ? dm.group(1) : dm.group(2)));
            event.setTitle(heading.substring(dm.end()).trim());
        } else {
            event.setEventTime(LocalDate.now().toString());
            event.setTitle(heading);
        }
        if (event.getTitle() == null || event.getTitle().isEmpty()) {
            event.setTitle(heading.length() > 50 ? heading.substring(0, 50) + "..." : heading);
        }

        String desc = body.replaceAll("```[\\s\\S]*?```", "").trim();
        event.setDescription(desc.length() > 500 ? desc.substring(0, 500) + "..." : desc);

        Matcher sm = SOLUTION_PATTERN.matcher(body);
        if (sm.find()) event.setSolution(sm.group(1).trim());

        event.setSeverity(assessSeverity(heading + " " + body));
        event.setEventType(detectEventType(heading, body));
        event.setStatus(event.getSolution() != null && !event.getSolution().isEmpty() ? "resolved" : "noted");
        return event;
    }

    private String normalizeDate(String raw) {
        if (raw == null) return LocalDate.now().toString();
        String clean = raw.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-").trim();
        try {
            if (clean.contains(":")) {
                return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-M-d H:mm")).toString();
            }
            return LocalDate.parse(clean, DateTimeFormatter.ofPattern("yyyy-M-d")).toString();
        } catch (DateTimeParseException e) {
            try {
                if (clean.matches("\\d{1,2}月\\d{1,2}日")) {
                    Matcher m = Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(clean);
                    if (m.matches()) {
                        int month = Integer.parseInt(m.group(1));
                        int day = Integer.parseInt(m.group(2));
                        return LocalDate.of(LocalDate.now().getYear(), month, day).toString();
                    }
                }
            } catch (Exception ignored) {}
            return clean;
        }
    }

    private String assessSeverity(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("宕机") || lower.contains("down") || lower.contains("崩溃") || lower.contains("不可用")
                || lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("critical")) {
            return "critical";
        }
        if (lower.contains("错误") || lower.contains("error") || lower.contains("失败") || lower.contains("fail")
                || lower.contains("超时") || lower.contains("timeout") || lower.contains("异常")) {
            return "high";
        }
        if (lower.contains("警告") || lower.contains("warn") || lower.contains("坑") || lower.contains("注意")) {
            return "medium";
        }
        return "low";
    }

    private String detectEventType(String heading, String body) {
        String text = (heading + " " + body).toLowerCase();
        if (text.contains("部署") || text.contains("deploy") || text.contains("安装") || text.contains("install")) return "deployment";
        if (text.contains("升级") || text.contains("update") || text.contains("upgrade")) return "upgrade";
        if (text.contains("故障") || text.contains("issue") || text.contains("bug") || text.contains("error")) return "incident";
        if (text.contains("配置") || text.contains("config") || text.contains("configur")) return "configuration";
        return "note";
    }
}
