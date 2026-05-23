package com.jones.activation.dto;

public class VerifyResponse {

    private boolean success;
    private String message;
    private String serialNumber;
    private String deviceId;
    private Long expireTime;
    private boolean expired;
    private boolean deviceMismatch;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public boolean isDeviceMismatch() { return deviceMismatch; }
    public void setDeviceMismatch(boolean deviceMismatch) { this.deviceMismatch = deviceMismatch; }

    public static VerifyResponseBuilder builder() {
        return new VerifyResponseBuilder();
    }

    public static class VerifyResponseBuilder {
        private boolean success;
        private String message;
        private String serialNumber;
        private String deviceId;
        private Long expireTime;
        private boolean expired;
        private boolean deviceMismatch;

        public VerifyResponseBuilder success(boolean success) { this.success = success; return this; }
        public VerifyResponseBuilder message(String message) { this.message = message; return this; }
        public VerifyResponseBuilder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }
        public VerifyResponseBuilder deviceId(String deviceId) { this.deviceId = deviceId; return this; }
        public VerifyResponseBuilder expireTime(Long expireTime) { this.expireTime = expireTime; return this; }
        public VerifyResponseBuilder expired(boolean expired) { this.expired = expired; return this; }
        public VerifyResponseBuilder deviceMismatch(boolean deviceMismatch) { this.deviceMismatch = deviceMismatch; return this; }

        public VerifyResponse build() {
            VerifyResponse response = new VerifyResponse();
            response.setSuccess(success);
            response.setMessage(message);
            response.setSerialNumber(serialNumber);
            response.setDeviceId(deviceId);
            response.setExpireTime(expireTime);
            response.setExpired(expired);
            response.setDeviceMismatch(deviceMismatch);
            return response;
        }
    }
}