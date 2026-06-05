package com.stilov.springboot_practice_2503.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stilov.springboot_practice_2503.entities.ReservationEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CacheConfiguration {

    @Bean
    public RedisTemplate<String, ReservationEntity> redisReservationTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper
    ){
        RedisTemplate<String, ReservationEntity> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        redisTemplate.setKeySerializer(new StringRedisSerializer());

        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, ReservationEntity.class);
        redisTemplate.setValueSerializer(serializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }
}
