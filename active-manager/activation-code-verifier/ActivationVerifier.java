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
        try {
            if (activationCode == null || activationCode.trim().isEmpty()) {
                return VerifyResult.fail("激活码不能为空");
            }

            String[] parts = activationCode.split("\\.");
            if (parts.length != 2) {
                return VerifyResult.fail("激活码格式无效");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|");
            if (payloadParts.length != 2) {
                return VerifyResult.fail("激活码载荷格式无效");
            }

            String serialNumber = payloadParts[0];
            long expireTimestamp;
            try {
                expireTimestamp = Long.parseLong(payloadParts[1]);
            } catch (NumberFormatException e) {
                return VerifyResult.fail("激活码过期时间格式无效");
            }

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            boolean verified = signature.verify(signatureBytes);

            if (!verified) {
                return VerifyResult.fail("激活码签名验证失败");
            }

            boolean expired = expireTimestamp < System.currentTimeMillis();
            if (expired) {
                return VerifyResult.fail("激活码已过期", serialNumber, expireTimestamp, true);
            }

            return VerifyResult.ok(serialNumber, expireTimestamp);
        } catch (Exception e) {
            return VerifyResult.fail("验证激活码异常: " + e.getMessage());
        }
    }

    public static class VerifyResult {
        private final boolean success;
        private final String message;
        private final String serialNumber;
        private final long expireTimestamp;
        private final boolean expired;

        private VerifyResult(boolean success, String message, String serialNumber, long expireTimestamp, boolean expired) {
            this.success = success;
            this.message = message;
            this.serialNumber = serialNumber;
            this.expireTimestamp = expireTimestamp;
            this.expired = expired;
        }

        public static VerifyResult ok(String serialNumber, long expireTimestamp) {
            return new VerifyResult(true, "验证成功", serialNumber, expireTimestamp, false);
        }

        public static VerifyResult fail(String message) {
            return new VerifyResult(false, message, null, 0, false);
        }

        public static VerifyResult fail(String message, String serialNumber, long expireTimestamp, boolean expired) {
            return new VerifyResult(false, message, serialNumber, expireTimestamp, expired);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getSerialNumber() { return serialNumber; }
        public long getExpireTimestamp() { return expireTimestamp; }
        public boolean isExpired() { return expired; }
    }
}
