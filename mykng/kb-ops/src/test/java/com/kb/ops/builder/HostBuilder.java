package com.kb.ops.builder;

import com.kb.ops.entity.Host;

import java.time.LocalDateTime;

/**
 * 主机测试数据工厂（SOP 2.5.3 Builder 模式）
 */
public class HostBuilder {

    private Long id = 1L;
    private String name = "web-01";
    private String ip = "192.168.1.100";
    private String tailscaleIp = "100.64.0.1";
    private Integer sshPort = 22;
    private String username = "root";
    private String passwordEncrypted = "encrypted-password-base64";
    private String role = "web";
    private Integer status = 1;
    private String tags = "production,web";
    private String remark = "Web 服务器";
    private Integer deleted = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    private HostBuilder() {}

    /**
     * 默认主机
     */
    public static HostBuilder aHost() {
        return new HostBuilder();
    }

    /**
     * 在线主机（status=1 运行中）
     */
    public static HostBuilder anOnlineHost() {
        return new HostBuilder().withStatus(1).withName("online-host");
    }

    /**
     * 离线主机（status=0 停机）
     */
    public static HostBuilder anOfflineHost() {
        return new HostBuilder().withStatus(0).withName("offline-host");
    }

    /**
     * 维护中主机（status=2）
     */
    public static HostBuilder aMaintenanceHost() {
        return new HostBuilder().withStatus(2).withName("maintenance-host");
    }

    public HostBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public HostBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public HostBuilder withIp(String ip) {
        this.ip = ip;
        return this;
    }

    public HostBuilder withTailscaleIp(String tailscaleIp) {
        this.tailscaleIp = tailscaleIp;
        return this;
    }

    public HostBuilder withSshPort(Integer sshPort) {
        this.sshPort = sshPort;
        return this;
    }

    public HostBuilder withUsername(String username) {
        this.username = username;
        return this;
    }

    public HostBuilder withPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
        return this;
    }

    public HostBuilder withRole(String role) {
        this.role = role;
        return this;
    }

    public HostBuilder withStatus(Integer status) {
        this.status = status;
        return this;
    }

    public HostBuilder withTags(String tags) {
        this.tags = tags;
        return this;
    }

    public HostBuilder withRemark(String remark) {
        this.remark = remark;
        return this;
    }

    public HostBuilder withDeleted(Integer deleted) {
        this.deleted = deleted;
        return this;
    }

    public HostBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public HostBuilder withUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    public Host build() {
        Host host = new Host();
        host.setId(id);
        host.setName(name);
        host.setIp(ip);
        host.setTailscaleIp(tailscaleIp);
        host.setSshPort(sshPort);
        host.setUsername(username);
        host.setPasswordEncrypted(passwordEncrypted);
        host.setRole(role);
        host.setStatus(status);
        host.setTags(tags);
        host.setRemark(remark);
        host.setDeleted(deleted);
        host.setCreatedAt(createdAt);
        host.setUpdatedAt(updatedAt);
        return host;
    }
}
