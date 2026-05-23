package com.jones.activation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("activation_record")
public class ActivationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String serialNumber;
    private String deviceId;
    private String activationCode;
    private Long expireTime;
    private LocalDateTime activatedTime;
    private Integer expireMinutes;
    private String initialSerial;
    private String machineCode;
    private String deviceAlias;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getActivationCode() { return activationCode; }
    public void setActivationCode(String activationCode) { this.activationCode = activationCode; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public LocalDateTime getActivatedTime() { return activatedTime; }
    public void setActivatedTime(LocalDateTime activatedTime) { this.activatedTime = activatedTime; }

    public Integer getExpireMinutes() { return expireMinutes; }
    public void setExpireMinutes(Integer expireMinutes) { this.expireMinutes = expireMinutes; }

    public String getInitialSerial() { return initialSerial; }
    public void setInitialSerial(String initialSerial) { this.initialSerial = initialSerial; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public String getDeviceAlias() { return deviceAlias; }
    public void setDeviceAlias(String deviceAlias) { this.deviceAlias = deviceAlias; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
