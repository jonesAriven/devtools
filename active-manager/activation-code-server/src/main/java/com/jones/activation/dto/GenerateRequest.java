package com.jones.activation.dto;

public class GenerateRequest {

    private String serialNumber;
    private Integer expireDays;

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public Integer getExpireDays() { return expireDays; }
    public void setExpireDays(Integer expireDays) { this.expireDays = expireDays; }
}
