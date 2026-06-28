package com.kb.intelligence.parser;

import org.springframework.stereotype.Component;

@Component
public class DocTypeDetector {

    public DocType detect(String fileName, String content) {
        String name = fileName.toLowerCase();

        if (name.contains("清单") || name.contains("速查") || name.contains("账密") || name.contains("服务器")
                || name.contains("列表") || name.contains("地址") || name.contains("host") || name.contains("server")) {
            return DocType.TABLE;
        }

        if (name.contains("踩坑") || name.contains("报告") || name.contains("故障") || name.contains("日志")
                || name.contains("health") || name.contains("incident") || name.contains("issue")
                || content.contains("## 问题") || content.contains("## 故障") || content.contains("## 坑")) {
            return DocType.TIMELINE;
        }

        if (content.contains("├──") || content.contains("mermaid") || content.contains("graph ")
                || content.contains("digraph") || content.contains("flowchart") || name.contains("架构")
                || name.contains("拓扑") || name.contains("依赖")) {
            return DocType.GRAPH;
        }

        if (name.contains("规则") || name.contains("规范") || name.contains("手册") || name.contains("指南")
                || name.contains("rule") || name.contains("convention") || name.contains("guide")) {
            return DocType.RULE;
        }

        if (name.contains("方案") || name.contains("部署") || name.contains("安装") || name.contains("配置")
                || name.contains("deploy") || name.contains("install") || name.contains("setup")
                || content.contains("## 步骤") || content.contains("## 命令") || content.contains("docker")
                || content.contains("```bash") || content.contains("```shell")) {
            return DocType.PLAN;
        }

        return DocType.GENERAL;
    }
}
