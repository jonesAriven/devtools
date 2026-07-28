package com.jones.activation.dto;

public class GenerateResponse {

    private boolean success;
    private String message;
    private String activationCode;
    private Long expireTime;
    private String serialNumber;
    private String deviceId;
    private String initialSerial;
    private String machineCode;
    private String downloadUrl;
    private String clientVersion;
    private Integer expireMinutes;
    private String expireLabel;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getActivationCode() { return activationCode; }
    public void setActivationCode(String activationCode) { this.activationCode = activationCode; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getInitialSerial() { return initialSerial; }
    public void setInitialSerial(String initialSerial) { this.initialSerial = initialSerial; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getClientVersion() { return clientVersion; }
    public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }

    public Integer getExpireMinutes() { return expireMinutes; }
    public void setExpireMinutes(Integer expireMinutes) { this.expireMinutes = expireMinutes; }

    public String getExpireLabel() { return expireLabel; }
    public void setExpireLabel(String expireLabel) { this.expireLabel = expireLabel; }

    public static GenerateResponseBuilder builder() {
        return new GenerateResponseBuilder();
    }

    public static class GenerateResponseBuilder {
        private boolean success;
        private String message;
        private String activationCode;
        private Long expireTime;
        private String serialNumber;
        private String deviceId;
        private String initialSerial;
        private String machineCode;
        private String downloadUrl;
        private String clientVersion;
        private Integer expireMinutes;
        private String expireLabel;

        public GenerateResponseBuilder success(boolean success) { this.success = success; return this; }
        public GenerateResponseBuilder message(String message) { this.message = message; return this; }
        public GenerateResponseBuilder activationCode(String activationCode) { this.activationCode = activationCode; return this; }
        public GenerateResponseBuilder expireTime(Long expireTime) { this.expireTime = expireTime; return this; }
        public GenerateResponseBuilder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }
        public GenerateResponseBuilder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public GenerateResponseBuilder initialSerial(String initialSerial) { this.initialSerial = initialSerial; return this; }
        public GenerateResponseBuilder machineCode(String machineCode) { this.machineCode = machineCode; return this; }
        public GenerateResponseBuilder downloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; return this; }
        public GenerateResponseBuilder clientVersion(String clientVersion) { this.clientVersion = clientVersion; return this; }
        public GenerateResponseBuilder expireMinutes(Integer expireMinutes) { this.expireMinutes = expireMinutes; return this; }
        public GenerateResponseBuilder expireLabel(String expireLabel) { this.expireLabel = expireLabel; return this; }

        public GenerateResponse build() {
            GenerateResponse response = new GenerateResponse();
            response.setSuccess(success);
            response.setMessage(message);
            response.setActivationCode(activationCode);
            response.setExpireTime(expireTime);
            response.setSerialNumber(serialNumber);
            response.setDeviceId(deviceId);
            response.setInitialSerial(initialSerial);
            response.setMachineCode(machineCode);
            response.setDownloadUrl(downloadUrl);
            response.setClientVersion(clientVersion);
            response.setExpireMinutes(expireMinutes);
            response.setExpireLabel(expireLabel);
            return response;
        }
    }
}
