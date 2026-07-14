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
    private static final String PAYLOAD_SEPARATOR = "|";
    private static final byte SERIAL_XOR_KEY = 0x5A;

    /**
     * 反转字节数组（原地反转）
     */
    private static void reverseByteArray(byte[] arr) {
        int len = arr.length;
        for (int i = 0; i < len / 2; i++) {
            byte tmp = arr[i];
            arr[i] = arr[len - 1 - i];
            arr[len - 1 - i] = tmp;
        }
    }

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

    public String generateActivationCode(String serialNumber, String deviceId, long expireTimestamp) {
        try {
            String payload = serialNumber + PAYLOAD_SEPARATOR + deviceId + PAYLOAD_SEPARATOR + expireTimestamp;
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payloadBytes);
            byte[] signatureBytes = signature.sign();

            // 反转签名字节序，兼容客户端 CryptoAPI 验证
            // 客户端 ActivationVerifier.cpp:217 做了 std::reverse()
            // 服务端先反转，客户端再反转 → 还原为原始正确签名 → 验证通过
            reverseByteArray(signatureBytes);

            String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
            String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            String activationCode = payloadBase64 + SEPARATOR + signatureBase64;
            log.info("生成激活码成功, 序列号: {}, 设备ID: {}, 过期时间: {}", serialNumber, deviceId, expireTimestamp);
            return activationCode;
        } catch (Exception e) {
            log.error("生成激活码失败", e);
            throw new RuntimeException("生成激活码失败", e);
        }
    }

    public String generateActivationCode(String serialNumber, long expireTimestamp) {
        return generateActivationCode(serialNumber, "", expireTimestamp);
    }

    public ActivationCodeParseResult parseAndVerify(String activationCode) {
        return parseAndVerify(activationCode, null);
    }

    public ActivationCodeParseResult parseAndVerify(String activationCode, String expectedDeviceId) {
        byte[] payloadBytes = null;
        byte[] signatureBytes = null;
        
        try {
            String[] parts = activationCode.split("\\.");
            if (parts.length != 2) {
                log.warn("激活码格式无效");
                return ActivationCodeParseResult.fail("激活码格式无效");
            }

            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signatureBytes = Base64.getUrlDecoder().decode(parts[1]);

            // 反转签名字节序（服务端生成时已反转，验证时需先还原）
            reverseByteArray(signatureBytes);

            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\" + PAYLOAD_SEPARATOR);
            
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
                log.warn("激活码载荷格式无效");
                return ActivationCodeParseResult.fail("激活码载荷格式无效");
            }

            if (expireTimestamp == -1) {
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

            if (expectedDeviceId != null && !expectedDeviceId.isEmpty() && 
                !deviceId.isEmpty() && !deviceId.equals(expectedDeviceId)) {
                log.warn("设备不匹配, 激活码绑定设备: {}, 当前设备: {}", deviceId, expectedDeviceId);
                return ActivationCodeParseResult.fail("设备不匹配", serialNumber, deviceId, expireTimestamp, true);
            }

            log.info("激活码验证成功, 序列号: {}, 设备ID: {}, 过期时间: {}", serialNumber, deviceId, expireTimestamp);
            return ActivationCodeParseResult.success(serialNumber, deviceId, expireTimestamp);
        } catch (Exception e) {
            log.error("验证激活码异常", e);
            return ActivationCodeParseResult.fail("验证激活码异常");
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
            log.warn("激活码过期时间格式无效");
            return -1;
        }
    }

    public static class ActivationCodeParseResult {
        private final boolean valid;
        private final String message;
        private final String serialNumber;
        private final String deviceId;
        private final long expireTimestamp;
        private final boolean deviceMismatch;

        private ActivationCodeParseResult(boolean valid, String message, String serialNumber, 
                                          String deviceId, long expireTimestamp, boolean deviceMismatch) {
            this.valid = valid;
            this.message = message;
            this.serialNumber = serialNumber;
            this.deviceId = deviceId;
            this.expireTimestamp = expireTimestamp;
            this.deviceMismatch = deviceMismatch;
        }

        public static ActivationCodeParseResult success(String serialNumber, String deviceId, long expireTimestamp) {
            return new ActivationCodeParseResult(true, "验证成功", serialNumber, deviceId, expireTimestamp, false);
        }

        public static ActivationCodeParseResult fail(String message) {
            return new ActivationCodeParseResult(false, message, null, null, 0, false);
        }

        public static ActivationCodeParseResult fail(String message, String serialNumber, String deviceId, 
                                                     long expireTimestamp, boolean deviceMismatch) {
            return new ActivationCodeParseResult(false, message, serialNumber, deviceId, expireTimestamp, deviceMismatch);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getSerialNumber() { return serialNumber; }
        public String getDeviceId() { return deviceId; }
        public long getExpireTimestamp() { return expireTimestamp; }
        public boolean isDeviceMismatch() { return deviceMismatch; }
    }

    public static SerialNumberParseResult decryptSerialNumber(String encryptedSerialNumber) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(encryptedSerialNumber);
            byte[] decrypted = new byte[encrypted.length];
            for (int i = 0; i < encrypted.length; i++) {
                decrypted[i] = (byte) (encrypted[i] ^ SERIAL_XOR_KEY);
            }
            String plainText = new String(decrypted, StandardCharsets.UTF_8);
            String[] parts = plainText.split("\\|");
            if (parts.length >= 3) {
                return new SerialNumberParseResult(true, "解析成功", parts[0], parts[1], parts[2]);
            }
            return new SerialNumberParseResult(false, "唯一序列号格式无效", null, null, null);
        } catch (Exception e) {
            log.error("解密唯一序列号失败", e);
            return new SerialNumberParseResult(false, "唯一序列号解密失败", null, null, null);
        }
    }

    public static class SerialNumberParseResult {
        private final boolean success;
        private final String message;
        private final String initialSerial;
        private final String deviceId;
        private final String machineCode;

        public SerialNumberParseResult(boolean success, String message, String initialSerial,
                                       String deviceId, String machineCode) {
            this.success = success;
            this.message = message;
            this.initialSerial = initialSerial;
            this.deviceId = deviceId;
            this.machineCode = machineCode;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getInitialSerial() { return initialSerial; }
        public String getDeviceId() { return deviceId; }
        public String getMachineCode() { return machineCode; }
    }
}