package com.library.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

/**
 * Configuration for Redis cache-aside engine (Member 2).
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.url:${REDIS_URL:}}")
    private String redisUrl;

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6380}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config;
        boolean useSsl = false;

        if (StringUtils.hasText(redisUrl)) {
            try {
                java.net.URI uri = java.net.URI.create(redisUrl.trim());
                useSsl = "rediss".equalsIgnoreCase(uri.getScheme());
                String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
                int port = uri.getPort() > 0 ? uri.getPort() : (useSsl ? 6380 : 6379);
                config = new RedisStandaloneConfiguration(host, port);
                String userInfo = uri.getUserInfo();
                if (StringUtils.hasText(userInfo)) {
                    String[] parts = userInfo.split(":", 2);
                    if (parts.length == 2) {
                        config.setPassword(parts[1]);
                    } else if (parts.length == 1) {
                        config.setPassword(parts[0]);
                    }
                }
            } catch (Exception e) {
                config = new RedisStandaloneConfiguration(redisHost, redisPort);
                if (StringUtils.hasText(redisPassword)) {
                    config.setPassword(redisPassword);
                }
            }
        } else {
            config = new RedisStandaloneConfiguration(redisHost, redisPort);
            if (StringUtils.hasText(redisPassword)) {
                config.setPassword(redisPassword);
            }
        }

        io.lettuce.core.SocketOptions socketOptions = io.lettuce.core.SocketOptions.builder()
                .connectTimeout(java.time.Duration.ofMillis(2000))
                .build();

        io.lettuce.core.ClientOptions clientOptions = io.lettuce.core.ClientOptions.builder()
                .socketOptions(socketOptions)
                .build();

        org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
                org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                        .commandTimeout(java.time.Duration.ofMillis(2000))
                        .clientOptions(clientOptions);

        if (useSsl) {
            clientConfigBuilder.useSsl();
        }

        return new LettuceConnectionFactory(config, clientConfigBuilder.build());
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new org.springframework.data.redis.core.StringRedisTemplate(connectionFactory);
    }
}
