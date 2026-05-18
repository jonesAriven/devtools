package com.jones.activation.verifier;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class ActivationVerifier {

    private final PublicKey publicKey;

    public ActivationVerifier(String publicKeyPem) {
        this.publicKey = parsePublicKey(publicKeyPem);
    }

    private static PublicKey parsePublicKey(String publicKeyPem) {
        try {
            String keyContent = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("解析公钥失败", e);
        }
    }

    public VerifyResult verify(String activationCode) {
        return verify(activationCode, null);
    }

    public VerifyResult verify(String activationCode, String expectedDeviceId) {
        byte[] payloadBytes = null;
        byte[] signatureBytes = null;

        try {
            if (activationCode == null || activationCode.trim().isEmpty()) {
                return VerifyResult.fail("激活码不能为空");
            }

            String[] parts = activationCode.split("\\.");
            if (parts.length != 2) {
                return VerifyResult.fail("激活码格式无效");
            }

            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signatureBytes = Base64.getUrlDecoder().decode(parts[1]);

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|");

            String serialNumber;
            String deviceId;
            long expireTimestamp;

            if (payloadParts.length == 2) {
                serialNumber = payloadParts[0];
                deviceId = "";
                expireTimestamp = parseExpireTime(payloadParts[1]);
            } else if (payloadParts.length == 3) {
                serialNumber = payloadParts[0];
                deviceId = payloadParts[1];
                expireTimestamp = parseExpireTime(payloadParts[2]);
            } else {
                return VerifyResult.fail("激活码载荷格式无效");
            }

            if (expireTimestamp == -1) {
                return VerifyResult.fail("激活码过期时间格式无效");
            }

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            boolean verified = signature.verify(signatureBytes);

            if (!verified) {
                return VerifyResult.fail("激活码签名验证失败");
            }

            if (expectedDeviceId != null && !expectedDeviceId.isEmpty() &&
                    !deviceId.isEmpty() && !deviceId.equals(expectedDeviceId)) {
                return VerifyResult.fail("设备不匹配", serialNumber, deviceId, expireTimestamp, true);
            }

            boolean expired = expireTimestamp < System.currentTimeMillis();
            if (expired) {
                return VerifyResult.fail("激活码已过期", serialNumber, deviceId, expireTimestamp, false);
            }

            return VerifyResult.ok(serialNumber, deviceId, expireTimestamp);
        } catch (Exception e) {
            return VerifyResult.fail("验证激活码异常");
        } finally {
            if (payloadBytes != null) {
                java.util.Arrays.fill(payloadBytes, (byte) 0);
            }
            if (signatureBytes != null) {
                java.util.Arrays.fill(signatureBytes, (byte) 0);
            }
        }
    }

    private long parseExpireTime(String timeStr) {
        try {
            return Long.parseLong(timeStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static class VerifyResult {
        private final boolean success;
        private final String message;
        private final String serialNumber;
        private final String deviceId;
        private final long expireTimestamp;
        private final boolean expired;
        private final boolean deviceMismatch;

        private VerifyResult(boolean success, String message, String serialNumber,
                            String deviceId, long expireTimestamp, boolean expired, boolean deviceMismatch) {
            this.success = success;
            this.message = message;
            this.serialNumber = serialNumber;
            this.deviceId = deviceId;
            this.expireTimestamp = expireTimestamp;
            this.expired = expired;
            this.deviceMismatch = deviceMismatch;
        }

        public static VerifyResult ok(String serialNumber, String deviceId, long expireTimestamp) {
            return new VerifyResult(true, "验证成功", serialNumber, deviceId, expireTimestamp, false, false);
        }

        public static VerifyResult fail(String message) {
            return new VerifyResult(false, message, null, null, 0, false, false);
        }

        public static VerifyResult fail(String message, String serialNumber, String deviceId,
                                        long expireTimestamp, boolean deviceMismatch) {
            return new VerifyResult(false, message, serialNumber, deviceId, expireTimestamp, !deviceMismatch, deviceMismatch);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getSerialNumber() { return serialNumber; }
        public String getDeviceId() { return deviceId; }
        public long getExpireTimestamp() { return expireTimestamp; }
        public boolean isExpired() { return expired; }
        public boolean isDeviceMismatch() { return deviceMismatch; }
    }
}