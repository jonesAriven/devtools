package com.jones.activation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("activation_log")
public class ActivationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private String serialNumber;
    private String deviceId;
    private String deviceAlias;
    private String eventType;
    private String eventMessage;
    private String clientIp;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceAlias() { return deviceAlias; }
    public void setDeviceAlias(String deviceAlias) { this.deviceAlias = deviceAlias; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventMessage() { return eventMessage; }
    public void setEventMessage(String eventMessage) { this.eventMessage = eventMessage; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
