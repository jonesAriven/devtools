package com.jones.activation.config;

import com.jones.activation.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

@Configuration
public class RsaKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyConfig.class);

    @Value("${activation.rsa.private-key-path}")
    private String privateKeyPath;

    @Value("${activation.rsa.public-key-path}")
    private String publicKeyPath;

    @Value("${activation.rsa.key-size}")
    private int keySize;

    @Bean
    public CryptoUtil cryptoUtil() throws IOException {
        Path privPath = Paths.get(privateKeyPath);
        Path pubPath = Paths.get(publicKeyPath);

        if (!Files.exists(privPath) || !Files.exists(pubPath)) {
            log.info("RSA密钥对不存在，正在生成新的密钥对...");
            generateAndSaveKeyPair(privPath, pubPath);
        }

        String privateKeyPem = Files.readString(privPath);
        String publicKeyPem = Files.readString(pubPath);

        PrivateKey privateKey = CryptoUtil.parsePrivateKey(privateKeyPem);
        PublicKey publicKey = CryptoUtil.parsePublicKey(publicKeyPem);

        log.info("RSA密钥对加载成功");
        return new CryptoUtil(privateKey, publicKey);
    }

    private void generateAndSaveKeyPair(Path privPath, Path pubPath) throws IOException {
        KeyPair keyPair = CryptoUtil.generateKeyPair(keySize);

        Files.createDirectories(privPath.getParent());

        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" +
                java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";

        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" +
                java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----\n";

        Files.writeString(privPath, privateKeyPem);
        Files.writeString(pubPath, publicKeyPem);

        log.info("RSA密钥对已生成并保存到: {} 和 {}", privPath.toAbsolutePath(), pubPath.toAbsolutePath());
    }
}
