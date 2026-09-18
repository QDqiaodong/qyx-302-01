package com.example.reviewsystem.service;

import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 集成测试环境无 Redis：提供 mock 连接工厂，避免 Lettuce 启动期反复连接真实端口。
 * ConfigService 对缓存读写已做容错（失败回退数据库直读），业务语义不受影响。
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    @ConditionalOnMissingBean
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }
}
