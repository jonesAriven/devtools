package com.jones.activation.dto;

public class GenerateResponse {

    private boolean success;
    private String message;
    private String activationCode;
    private Long expireTime;
    private String serialNumber;

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

    public static GenerateResponseBuilder builder() {
        return new GenerateResponseBuilder();
    }

    public static class GenerateResponseBuilder {
        private boolean success;
        private String message;
        private String activationCode;
        private Long expireTime;
        private String serialNumber;

        public GenerateResponseBuilder success(boolean success) { this.success = success; return this; }
        public GenerateResponseBuilder message(String message) { this.message = message; return this; }
        public GenerateResponseBuilder activationCode(String activationCode) { this.activationCode = activationCode; return this; }
        public GenerateResponseBuilder expireTime(Long expireTime) { this.expireTime = expireTime; return this; }
        public GenerateResponseBuilder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }

        public GenerateResponse build() {
            GenerateResponse response = new GenerateResponse();
            response.setSuccess(success);
            response.setMessage(message);
            response.setActivationCode(activationCode);
            response.setExpireTime(expireTime);
            response.setSerialNumber(serialNumber);
            return response;
        }
    }
}
