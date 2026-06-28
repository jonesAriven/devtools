package com.kb.intelligence.parser;

import com.kb.intelligence.entity.KnCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommandExtractor {

    private static final Set<String> DANGEROUS_COMMANDS = new HashSet<>(Arrays.asList(
            "rm -rf", "mkfs", "dd if=", "shutdown", "reboot", "halt",
            "DROP TABLE", "DROP DATABASE", "TRUNCATE", "DELETE FROM",
            "iptables -F", "chmod 777", "chown -R"
    ));

    private static final Set<String> COMMON_PREFIXES = new HashSet<>(Arrays.asList(
            "docker", "docker-compose", "kubectl", "systemctl", "service",
            "apt", "apt-get", "yum", "dnf", "apk", "npm", "pnpm", "yarn",
            "curl", "wget", "git", "ssh", "scp", "tar", "unzip",
            "mysql", "redis-cli", "psql", "mongo", "mongosh",
            "nginx", "java", "mvn", "gradle", "node", "python", "pip",
            "ufw", "firewall-cmd", "netstat", "ss ", "ps ", "top",
            "mkdir", "cp ", "mv ", "cat ", "less", "tail", "vi ", "vim ",
            "export ", "source", "echo", "cd ", "ls ", "chmod", "chown",
            "nohup", "screen", "tmux", "crontab", "sysctl",
            "helm", "ansible", "terraform"
    ));

    private static final Pattern DOCKER_RUN = Pattern.compile("docker\\s+run[\\s\\S]+?(?=\\n\\n|$)", Pattern.MULTILINE);
    private static final Pattern DOCKER_COMPOSE = Pattern.compile("docker-compose[^\\n]+");
    private static final Pattern BASH_PROMPT = Pattern.compile("^[\\$#>]\\s*(.+)$", Pattern.MULTILINE);

    public List<KnCommand> extractCommands(Long docId, List<MarkdownParser.CommandBlock> blocks) {
        List<KnCommand> commands = new ArrayList<>();
        for (MarkdownParser.CommandBlock block : blocks) {
            String lang = block.getLang();
            if (lang == null) lang = "text";
            if (lang.equals("bash") || lang.equals("shell") || lang.equals("sh") || lang.equals("cmd") || lang.equals("powershell")) {
                commands.addAll(extractFromCodeBlock(docId, block.getCode(), lang));
            }
        }
        return commands;
    }

    private List<KnCommand> extractFromCodeBlock(Long docId, String code, String lang) {
        List<KnCommand> commands = new ArrayList<>();
        String[] lines = code.split("\\n");
        StringBuilder currentCmd = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (currentCmd.length() > 0) {
                    addCommand(commands, docId, currentCmd.toString().trim(), lang);
                    currentCmd.setLength(0);
                }
                continue;
            }
            if (trimmed.startsWith("#")) continue;

            String cleanLine = trimmed.replaceAll("^[\\$#>]\\s*", "");
            if (currentCmd.length() > 0 && trimmed.endsWith("\\")) {
                currentCmd.append(cleanLine, 0, cleanLine.length() - 1).append(" ");
            } else if (cleanLine.endsWith("\\")) {
                currentCmd.append(cleanLine, 0, cleanLine.length() - 1).append(" ");
            } else {
                currentCmd.append(cleanLine);
                if (isCommand(currentCmd.toString())) {
                    addCommand(commands, docId, currentCmd.toString().trim(), lang);
                }
                currentCmd.setLength(0);
            }
        }
        if (currentCmd.length() > 0) {
            if (isCommand(currentCmd.toString())) {
                addCommand(commands, docId, currentCmd.toString().trim(), lang);
            }
        }
        return commands;
    }

    private boolean isCommand(String cmd) {
        String lower = cmd.toLowerCase().trim();
        if (lower.length() < 3) return false;
        for (String prefix : COMMON_PREFIXES) {
            if (lower.startsWith(prefix.toLowerCase())) return true;
        }
        return lower.contains("--") || lower.contains(" -") || lower.contains("|") || lower.contains(">");
    }

    private void addCommand(List<KnCommand> commands, Long docId, String cmdStr, String lang) {
        if (cmdStr.isEmpty()) return;
        KnCommand cmd = new KnCommand();
        cmd.setDocId(docId);
        cmd.setCommand(cmdStr);
        cmd.setOsType(lang.equals("powershell") || lang.equals("cmd") ? "windows" : "linux");
        cmd.setCategory(categorize(cmdStr));
        cmd.setRiskLevel(assessRisk(cmdStr));
        commands.add(cmd);
    }

    private String categorize(String cmd) {
        String lower = cmd.toLowerCase();
        if (lower.startsWith("docker")) return "container";
        if (lower.startsWith("kubectl") || lower.startsWith("helm")) return "k8s";
        if (lower.startsWith("systemctl") || lower.startsWith("service")) return "service";
        if (lower.startsWith("apt") || lower.startsWith("yum") || lower.startsWith("dnf") || lower.startsWith("apk")) return "package";
        if (lower.startsWith("mysql") || lower.startsWith("psql") || lower.startsWith("redis")) return "database";
        if (lower.startsWith("git")) return "git";
        if (lower.startsWith("curl") || lower.startsWith("wget")) return "network";
        if (lower.startsWith("ssh") || lower.startsWith("scp")) return "ssh";
        if (lower.startsWith("mkdir") || lower.startsWith("cp") || lower.startsWith("mv") || lower.startsWith("rm")) return "file";
        if (lower.startsWith("npm") || lower.startsWith("pnpm") || lower.startsWith("yarn")) return "package";
        if (lower.startsWith("nginx") || lower.startsWith("ufw") || lower.startsWith("firewall")) return "network";
        return "other";
    }

    private String assessRisk(String cmd) {
        String lower = cmd.toLowerCase();
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (lower.contains(dangerous.toLowerCase())) return "high";
        }
        if (lower.startsWith("rm ") || lower.startsWith("kill ") || lower.contains("sudo")) return "medium";
        return "low";
    }
}
