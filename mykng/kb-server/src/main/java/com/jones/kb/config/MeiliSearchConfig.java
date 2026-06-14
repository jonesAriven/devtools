package com.jones.kb.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "meilisearch")
public class MeiliSearchConfig {

    private String host;
    private String apiKey;

    @Bean
    public Client meiliSearchClient() {
        return new Client(new Config(host, apiKey));
    }
}
