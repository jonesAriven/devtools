package com.kb.intelligence.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class LlmClient {

    @Value("${kb.intelligence.llm.enabled:false}")
    private boolean enabled;

    @Value("${kb.intelligence.llm.endpoint:}")
    private String endpoint;

    @Value("${kb.intelligence.llm.api-key:}")
    private String apiKey;

    @Value("${kb.intelligence.llm.model:qwen-turbo}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isAvailable() {
        return enabled && endpoint != null && !endpoint.isBlank();
    }

    public Optional<JsonNode> extractEntities(String content, String docType) {
        if (!isAvailable()) {
            log.debug("LLM未配置，跳过实体提取");
            return Optional.empty();
        }

        try {
            String prompt = buildExtractPrompt(content, docType);
            Map<String, Object> request = buildRequest(prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("Authorization", "Bearer " + apiKey);
            }

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);
            ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String text = root.path("choices").path(0).path("message").path("content").asText();
                text = extractJsonFromText(text);
                return Optional.of(objectMapper.readTree(text));
            }
        } catch (Exception e) {
            log.warn("LLM提取实体失败: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Map<String, Object> buildRequest(String prompt) {
        Map<String, Object> req = new HashMap<>();
        req.put("model", model);
        req.put("temperature", 0.1);
        req.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", "你是一个运维文档信息提取助手。从给定的Markdown文本中提取结构化实体信息，只输出JSON，不要任何解释。");
        messages.add(sysMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        req.put("messages", messages);
        return req;
    }

    private String buildExtractPrompt(String content, String docType) {
        String truncated = content.length() > 6000 ? content.substring(0, 6000) : content;
        return "请从以下Markdown文档中提取结构化实体，输出JSON格式。文档类型: " + docType + "\n\n" +
                "输出格式:\n" +
                "{\n" +
                "  \"hosts\": [{\"name\":\"\",\"ip\":\"\",\"role\":\"\",\"os\":\"\",\"tags\":\"\"}],\n" +
                "  \"services\": [{\"name\":\"\",\"type\":\"\",\"port\":0,\"version\":\"\"}],\n" +
                "  \"commands\": [{\"command\":\"\",\"description\":\"\",\"category\":\"\",\"risk\":\"low|medium|high\"}],\n" +
                "  \"domains\": [{\"domain\":\"\",\"target\":\"\",\"port\":0}],\n" +
                "  \"summary\": \"文档摘要（100字内）\"\n" +
                "}\n\n" +
                "文档内容:\n" + truncated;
    }

    private String extractJsonFromText(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
