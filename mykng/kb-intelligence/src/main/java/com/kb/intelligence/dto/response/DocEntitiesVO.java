package com.kb.intelligence.dto.response;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocEntitiesVO {
    private Long docId;
    private String title;
    private List<HostVO> hosts = new ArrayList<>();
    private List<ServiceVO> services = new ArrayList<>();
    private List<PortVO> ports = new ArrayList<>();
    private List<CredentialVO> credentials = new ArrayList<>();
    private List<DomainVO> domains = new ArrayList<>();
    private List<CommandVO> commands = new ArrayList<>();
    private List<TimelineVO> timelines = new ArrayList<>();
    private Integer totalEntities = 0;

    @Data
    public static class HostVO {
        private Long id;
        private String name;
        private String ip;
        private String tailscaleIp;
        private Integer sshPort;
        private String username;
        private String role;
        private String osType;
        private String status;
    }

    @Data
    public static class ServiceVO {
        private Long id;
        private Long hostId;
        private String name;
        private String serviceType;
        private String version;
        private String status;
    }

    @Data
    public static class PortVO {
        private Long id;
        private Long hostId;
        private Long serviceId;
        private Integer port;
        private String protocol;
        private String accessUrl;
        private Integer exposed;
    }

    @Data
    public static class CredentialVO {
        private Long id;
        private Long hostId;
        private String credType;
        private String username;
        private String password;
        private String passwordHint;
    }

    @Data
    public static class DomainVO {
        private Long id;
        private String domain;
        private String subDomain;
        private Long targetHostId;
        private Integer targetPort;
        private String status;
    }

    @Data
    public static class CommandVO {
        private Long id;
        private String command;
        private String description;
        private String category;
        private String riskLevel;
        private String osType;
    }

    @Data
    public static class TimelineVO {
        private Long id;
        private Long docId;
        private String eventTime;
        private String eventType;
        private String title;
        private String description;
        private String severity;
        private String status;
        private String solution;
    }
}
