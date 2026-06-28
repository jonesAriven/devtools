package com.kb.intelligence.parser;

import com.kb.intelligence.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TableParser implements DocParser {

    private static final Pattern IP_PATTERN = Pattern.compile(
            "(?:(?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})|(?:192\\.168\\.\\d{1,3}\\.\\d{1,3})|(?:172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})|(?:100\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}))"
    );
    private static final Pattern PUBLIC_IP_PATTERN = Pattern.compile(
            "\\b((?:[0-9]{1,3}\\.){3}[0-9]{1,3})\\b"
    );
    private static final Pattern PORT_PATTERN = Pattern.compile("(?:port|端口|:)(\\d{2,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_PASS_PATTERN = Pattern.compile(
            "(?:用户名|账号|user|username|账号)[：: ]+([^\\s|]+)[\\s|/]+(?:密码|password|pwd)[：: ]+([^\\s|]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PASSWORD_LINE_PATTERN = Pattern.compile(
            "(?:密码|password|pwd)[：: ]+([^\\s|,，;；]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern USER_LINE_PATTERN = Pattern.compile(
            "(?:用户名|账号|user|username)[：: ]+([^\\s|,，;；]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(DocType docType) {
        return docType == DocType.TABLE;
    }

    @Override
    public ParseResult parse(String filePath, String fileName, String content, ParseResult result) {
        log.debug("TableParser 解析: {}", fileName);

        List<String[]> tableRows = extractTableRows(content);
        List<Map<String, String>> tableMaps = tableRowsToMaps(tableRows);

        for (Map<String, String> row : tableMaps) {
            KnHost host = extractHost(row);
            if (host != null) {
                result.getHosts().add(host);
                Long hostIdx = (long) result.getHosts().size();

                KnService svc = extractService(row, hostIdx);
                if (svc != null) result.getServices().add(svc);

                extractPorts(row, hostIdx, svc != null ? (long) result.getServices().size() : null).forEach(result.getPorts()::add);
                KnCredential cred = extractCredential(row, hostIdx);
                if (cred != null) result.getCredentials().add(cred);
            }
        }

        extractInlineHosts(content, result);
        return result;
    }

    private List<String[]> extractTableRows(String content) {
        List<String[]> rows = new ArrayList<>();
        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (trimmed.matches("^\\|\\s*[-:]+\\s*(\\|\\s*[-:]+\\s*)*\\|$")) continue;
                String[] cells = trimmed.split("\\|", -1);
                List<String> cleanCells = new ArrayList<>();
                for (int i = 1; i < cells.length - 1; i++) {
                    cleanCells.add(cells[i].trim().replaceAll("\\*+", ""));
                }
                if (!cleanCells.isEmpty()) {
                    rows.add(cleanCells.toArray(new String[0]));
                }
            }
        }
        return rows;
    }

    private List<Map<String, String>> tableRowsToMaps(List<String[]> rows) {
        List<Map<String, String>> maps = new ArrayList<>();
        if (rows.isEmpty()) return maps;
        String[] headers = rows.get(0);
        for (int i = 1; i < rows.size(); i++) {
            Map<String, String> map = new LinkedHashMap<>();
            String[] row = rows.get(i);
            for (int j = 0; j < headers.length; j++) {
                String key = headers[j].toLowerCase();
                String val = j < row.length ? row[j] : "";
                map.put(key, val);
            }
            maps.add(map);
        }
        return maps;
    }

    private KnHost extractHost(Map<String, String> row) {
        String ip = null, name = null, tailscaleIp = null, username = null, password = null, role = null;
        String osType = null, osVersion = null, remark = null;

        for (Map.Entry<String, String> entry : row.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (val.isEmpty()) continue;

            if (key.contains("ip") && !key.contains("tailscale") && !key.contains("公网") && !key.contains("public")) {
                ip = val;
            } else if (key.contains("tailscale") || key.contains("私网") || key.contains("内网")) {
                tailscaleIp = val;
            } else if (key.contains("公网") || key.contains("public") || key.contains("外网")) {
                remark = (remark == null ? "" : remark + " ") + "公网IP:" + val;
            } else if (key.contains("名称") || key.contains("主机") || key.contains("hostname") || key.contains("name") || key.contains("别名")) {
                name = val;
            } else if (key.contains("用户名") || key.contains("user") || key.contains("账号")) {
                username = val;
            } else if (key.contains("密码") || key.contains("pass") || key.contains("pwd")) {
                password = val;
            } else if (key.contains("角色") || key.contains("role") || key.contains("用途")) {
                role = val;
            } else if (key.contains("系统") || key.contains("os") || key.contains("版本")) {
                osType = val;
            } else if (key.contains("备注") || key.contains("remark") || key.contains("说明")) {
                remark = val;
            }
        }

        if (ip == null) {
            for (String val : row.values()) {
                Matcher m = IP_PATTERN.matcher(val);
                if (m.find()) {
                    ip = m.group();
                    break;
                }
            }
        }
        if (ip == null) return null;

        KnHost host = new KnHost();
        host.setIp(ip);
        host.setTailscaleIp(tailscaleIp);
        host.setName(name != null ? name : ip);
        host.setSshPort(22);
        host.setUsername(username);
        host.setPasswordEncrypted(password);
        host.setRole(role);
        host.setOsType(osType);
        host.setOsVersion(osVersion);
        host.setRemark(remark);
        host.setStatus("running");
        return host;
    }

    private KnService extractService(Map<String, String> row, Long hostIdx) {
        String svcName = null, svcType = null, version = null, port = null, remark = null;
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (val.isEmpty()) continue;
            if (key.contains("服务") || key.contains("service") || key.contains("应用") || key.contains("软件")) {
                svcName = val;
            } else if (key.contains("类型") || key.contains("type")) {
                svcType = val;
            } else if (key.contains("版本")) {
                version = val;
            }
        }
        if (svcName == null) return null;

        KnService svc = new KnService();
        svc.setHostId(hostIdx);
        svc.setName(svcName);
        svc.setServiceType(svcType);
        svc.setVersion(version);
        svc.setStatus("running");
        svc.setRemark(remark);
        return svc;
    }

    private List<KnPort> extractPorts(Map<String, String> row, Long hostIdx, Long svcIdx) {
        List<KnPort> ports = new ArrayList<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (val.isEmpty()) continue;

            Matcher pm = PORT_PATTERN.matcher(key + " " + val);
            while (pm.find()) {
                try {
                    int portNum = Integer.parseInt(pm.group(1));
                    if (portNum < 1 || portNum > 65535) continue;
                    KnPort p = new KnPort();
                    p.setHostId(hostIdx);
                    p.setServiceId(svcIdx);
                    p.setPort(portNum);
                    p.setProtocol("tcp");
                    p.setExposed(0);
                    ports.add(p);
                } catch (NumberFormatException ignored) {}
            }
        }
        return ports;
    }

    private KnCredential extractCredential(Map<String, String> row, Long hostIdx) {
        String username = null, password = null, credType = "ssh";
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (val.isEmpty()) continue;
            if (key.contains("用户") || key.contains("user") || key.contains("账号")) username = val;
            if (key.contains("密码") || key.contains("pass")) password = val;
        }
        if (username == null && password == null) return null;

        KnCredential c = new KnCredential();
        c.setHostId(hostIdx);
        c.setCredType(credType);
        c.setUsername(username);
        c.setPasswordEncrypted(password);
        return c;
    }

    private void extractInlineHosts(String content, ParseResult result) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            if (line.trim().startsWith("|")) continue;
            Matcher m = IP_PATTERN.matcher(line);
            while (m.find()) {
                String ip = m.group();
                boolean exists = result.getHosts().stream().anyMatch(h -> ip.equals(h.getIp()));
                if (!exists) {
                    KnHost host = new KnHost();
                    host.setIp(ip);
                    host.setName(ip);
                    host.setSshPort(22);
                    host.setStatus("running");

                    Matcher um = USER_LINE_PATTERN.matcher(line);
                    if (um.find()) host.setUsername(um.group(1));
                    Matcher pm = PASSWORD_LINE_PATTERN.matcher(line);
                    if (pm.find()) host.setPasswordEncrypted(pm.group(1));

                    result.getHosts().add(host);
                }
            }
        }
    }
}
