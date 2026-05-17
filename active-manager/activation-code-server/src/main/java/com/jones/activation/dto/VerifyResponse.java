package com.jones.activation.dto;

public class VerifyResponse {

    private boolean success;
    private String message;
    private String serialNumber;
    private Long expireTime;
    private boolean expired;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(Long expireTime) { this.expireTime = expireTime; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public static VerifyResponseBuilder builder() {
        return new VerifyResponseBuilder();
    }

    public static class VerifyResponseBuilder {
        private boolean success;
        private String message;
        private String serialNumber;
        private Long expireTime;
        private boolean expired;

        public VerifyResponseBuilder success(boolean success) { this.success = success; return this; }
        public VerifyResponseBuilder message(String message) { this.message = message; return this; }
        public VerifyResponseBuilder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }
        public VerifyResponseBuilder expireTime(Long expireTime) { this.expireTime = expireTime; return this; }
        public VerifyResponseBuilder expired(boolean expired) { this.expired = expired; return this; }

        public VerifyResponse build() {
            VerifyResponse response = new VerifyResponse();
            response.setSuccess(success);
            response.setMessage(message);
            response.setSerialNumber(serialNumber);
            response.setExpireTime(expireTime);
            response.setExpired(expired);
            return response;
        }
    }
}
