package com.kb.portal.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CryptoUtil {

    private final AES aes;

    public CryptoUtil(@Value("${portal.encryption.key:PortalSecretKey123!}") String key) {
        byte[] keyBytes = DigestUtil.sha256(key);
        this.aes = SecureUtil.aes(keyBytes);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        return aes.encryptHex(plainText);
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            return aes.decryptStr(cipherText);
        } catch (Exception e) {
            return null;
        }
    }
}
