package com.kb.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "jwt.secret=YourSuperSecretKeyForJwtTokenGenerationMustBe256BitsLong!!",
    "jwt.access-token-expiration=900000",
    "jwt.refresh-token-expiration=604800000",
    "crypto.aes-key=YWVzLTI1Ni1nY20ta2V5LTMyLWJ5dGVzISE=",
    "api-token.prefix=kb_",
    "api-token.expire-days=365"
})
class KbAuthApplicationTests {

    @Test
    void contextLoads() {
    }
}
