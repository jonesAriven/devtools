package com.kb.intelligence.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.kb.intelligence.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanDocParser implements DocParser {

    private final LlmClient llmClient;

    @Override
    public boolean supports(DocType docType) {
        return docType == DocType.PLAN;
    }

    @Override
    public ParseResult parse(String filePath, String fileName, String content, ParseResult result) {
        log.debug("PlanDocParser 解析: {}", fileName);

        Optional<JsonNode> llmResult = llmClient.extractEntities(content, "PLAN");
        if (llmResult.isPresent()) {
            try {
                parseLlmResult(llmResult.get(), result);
            } catch (Exception e) {
                log.warn("LLM结果解析失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private void parseLlmResult(JsonNode root, ParseResult result) {
        JsonNode hosts = root.path("hosts");
        if (hosts.isArray()) {
            for (JsonNode h : hosts) {
                KnHost host = new KnHost();
                host.setName(h.path("name").asText());
                host.setIp(h.path("ip").asText());
                host.setRole(h.path("role").asText());
                host.setOsType(h.path("os").asText());
                host.setTags(h.path("tags").asText());
                host.setStatus("running");
                host.setSshPort(22);
                if (host.getIp() != null && !host.getIp().isBlank()) {
                    boolean exists = result.getHosts().stream().anyMatch(x -> host.getIp().equals(x.getIp()));
                    if (!exists) result.getHosts().add(host);
                }
            }
        }

        JsonNode services = root.path("services");
        if (services.isArray()) {
            for (JsonNode s : services) {
                KnService svc = new KnService();
                svc.setName(s.path("name").asText());
                svc.setServiceType(s.path("type").asText());
                svc.setVersion(s.path("version").asText());
                svc.setStatus("running");
                if (svc.getName() != null && !svc.getName().isBlank()) {
                    result.getServices().add(svc);
                    if (s.has("port") && s.get("port").asInt() > 0) {
                        KnPort port = new KnPort();
                        port.setPort(s.get("port").asInt());
                        port.setProtocol("tcp");
                        port.setServiceId((long) result.getServices().size());
                        port.setExposed(0);
                        result.getPorts().add(port);
                    }
                }
            }
        }

        JsonNode commands = root.path("commands");
        if (commands.isArray()) {
            for (JsonNode c : commands) {
                KnCommand cmd = new KnCommand();
                cmd.setCommand(c.path("command").asText());
                cmd.setDescription(c.path("description").asText());
                cmd.setCategory(c.path("category").asText("other"));
                cmd.setRiskLevel(c.path("risk").asText("low"));
                cmd.setOsType("linux");
                if (cmd.getCommand() != null && !cmd.getCommand().isBlank()) {
                    result.getCommands().add(cmd);
                }
            }
        }

        JsonNode domains = root.path("domains");
        if (domains.isArray()) {
            for (JsonNode d : domains) {
                KnDomain dom = new KnDomain();
                dom.setDomain(d.path("domain").asText());
                dom.setTargetService(d.path("target").asText());
                if (d.has("port") && d.get("port").asInt() > 0) {
                    dom.setTargetPort(d.get("port").asInt());
                }
                dom.setStatus("active");
                if (dom.getDomain() != null && !dom.getDomain().isBlank()) {
                    result.getDomains().add(dom);
                }
            }
        }

        String summary = root.path("summary").asText();
        if (!summary.isBlank() && result.getDocMeta() != null) {
            result.getDocMeta().setSummary(summary);
        }
    }
}
