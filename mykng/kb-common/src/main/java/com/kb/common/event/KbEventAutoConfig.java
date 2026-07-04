package com.kb.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * kb-common 事件总线自动配置（M4 重构）
 * <p>
 * 下游服务通过 @Import(KbEventAutoConfig.class) 引入，自动获得 EventBus Bean。
 * <p>
 * 条件：classpath 中存在 StringRedisTemplate 且已注册 Bean（即下游服务已引入 spring-boot-starter-data-redis）。
 */
@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
public class KbEventAutoConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(EventBus.class)
    public EventBus eventBus(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisStreamEventBus(redisTemplate, objectMapper);
    }
}
