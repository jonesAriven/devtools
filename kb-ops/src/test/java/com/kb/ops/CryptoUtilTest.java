package com.kb.ops;

import com.kb.ops.util.CryptoUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES-256-GCM 加密工具单元测试。
 * 验证主机密码的加解密往返、空值处理与随机 IV。
 */
class CryptoUtilTest {

    private static final String AES_KEY = "Y2Iqb3BzLWFlcy0yNTYta2V5LWZvci1lbmNyeXB0aW9u";

    private CryptoUtil newUtil() {
        return new CryptoUtil(AES_KEY);
    }

    @Test
    void encryptDecryptRoundTrip() {
        CryptoUtil util = newUtil();
        String plain = "mySecretPassword123!@#中文";
        String encrypted = util.encrypt(plain);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, util.decrypt(encrypted));
    }

    @Test
    void handlesNull() {
        CryptoUtil util = newUtil();
        assertNull(util.encrypt(null));
        assertNull(util.decrypt(null));
        assertNull(util.decrypt(""));
    }

    @Test
    void eachEncryptionUsesRandomIv() {
        CryptoUtil util = newUtil();
        String plain = "same-password";
        String e1 = util.encrypt(plain);
        String e2 = util.encrypt(plain);
        assertNotEquals(e1, e2, "每次加密应使用随机 IV，密文应不同");
        assertEquals(plain, util.decrypt(e1));
        assertEquals(plain, util.decrypt(e2));
    }
}
