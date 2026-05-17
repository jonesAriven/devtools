package com.jones.activation.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class CryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);

    private static final String RSA_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String SEPARATOR = ".";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public CryptoUtil(PrivateKey privateKey, PublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static KeyPair generateKeyPair(int keySize) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyPairGenerator.initialize(keySize);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            log.error("生成RSA密钥对失败", e);
            throw new RuntimeException("生成RSA密钥对失败", e);
        }
    }

    public static PrivateKey parsePrivateKey(String privateKeyPem) {
        try {
            String keyContent = privateKeyPem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            log.error("解析私钥失败", e);
            throw new RuntimeException("解析私钥失败", e);
        }
    }

    public static PublicKey parsePublicKey(String publicKeyPem) {
        try {
            String keyContent = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyContent);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            log.error("解析公钥失败", e);
            throw new RuntimeException("解析公钥失败", e);
        }
    }

    public String generateActivationCode(String serialNumber, long expireTimestamp) {
        try {
            String payload = serialNumber + "|" + expireTimestamp;
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payloadBytes);
            byte[] signatureBytes = signature.sign();

            String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
            String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            String activationCode = payloadBase64 + SEPARATOR + signatureBase64;
            log.info("生成激活码成功, 序列号: {}, 过期时间: {}", serialNumber, expireTimestamp);
            return activationCode;
        } catch (Exception e) {
            log.error("生成激活码失败", e);
            throw new RuntimeException("生成激活码失败", e);
        }
    }

    public ActivationCodeParseResult parseAndVerify(String activationCode) {
        try {
            String[] parts = activationCode.split("\\.");
            if (parts.length != 2) {
                log.warn("激活码格式无效");
                return ActivationCodeParseResult.fail("激活码格式无效");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|");
            if (payloadParts.length != 2) {
                log.warn("激活码载荷格式无效");
                return ActivationCodeParseResult.fail("激活码载荷格式无效");
            }

            String serialNumber = payloadParts[0];
            long expireTimestamp;
            try {
                expireTimestamp = Long.parseLong(payloadParts[1]);
            } catch (NumberFormatException e) {
                log.warn("激活码过期时间格式无效");
                return ActivationCodeParseResult.fail("激活码过期时间格式无效");
            }

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            boolean verified = signature.verify(signatureBytes);

            if (!verified) {
                log.warn("激活码签名验证失败, 序列号: {}", serialNumber);
                return ActivationCodeParseResult.fail("激活码签名验证失败");
            }

            log.info("激活码验证成功, 序列号: {}, 过期时间: {}", serialNumber, expireTimestamp);
            return ActivationCodeParseResult.success(serialNumber, expireTimestamp);
        } catch (Exception e) {
            log.error("验证激活码异常", e);
            return ActivationCodeParseResult.fail("验证激活码异常: " + e.getMessage());
        }
    }

    public static class ActivationCodeParseResult {
        private final boolean valid;
        private final String message;
        private final String serialNumber;
        private final long expireTimestamp;

        private ActivationCodeParseResult(boolean valid, String message, String serialNumber, long expireTimestamp) {
            this.valid = valid;
            this.message = message;
            this.serialNumber = serialNumber;
            this.expireTimestamp = expireTimestamp;
        }

        public static ActivationCodeParseResult success(String serialNumber, long expireTimestamp) {
            return new ActivationCodeParseResult(true, "验证成功", serialNumber, expireTimestamp);
        }

        public static ActivationCodeParseResult fail(String message) {
            return new ActivationCodeParseResult(false, message, null, 0);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public long getExpireTimestamp() {
            return expireTimestamp;
        }
    }
}
