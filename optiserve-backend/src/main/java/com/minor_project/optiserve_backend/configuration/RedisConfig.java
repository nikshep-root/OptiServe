package com.minor_project.optiserve_backend.configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(
            //SETUP THE CONNECTION
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        //SETUP THE CONFIGS IE THE TIME TO STAY IN CACHE ETC.
        RedisCacheConfiguration config =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        )
                        //SETUP SERIALIZER TO CONVERT JAVA TO JSON(VALUES ARE CACHED IN JSONish WAY)
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                //GENERIC JACKSON JSON REDIS SERIALIZER IMPLEMENTS IT
                                                new GenericJacksonJsonRedisSerializer(objectMapper)
                                        )
                        );
        //BUILD THE CONNECTION
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
