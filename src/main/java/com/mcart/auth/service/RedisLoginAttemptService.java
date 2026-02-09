package com.mcart.auth.service;

import com.mcart.auth.entity.AuthIdentityEntity;
import com.mcart.auth.model.LoginAttemptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String KEY_PREFIX = "auth:login:fail:";

    private final StringRedisTemplate redisTemplate;
    private final LoginAttemptProperties props;
    private final Clock clock = Clock.systemUTC();

    private String key(UUID authIdentityId) {
        return KEY_PREFIX + authIdentityId;
    }

    @Override
    public void recordFailure(AuthIdentityEntity identity) {

        String key = key(identity.getAuthIdentityId());

        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            // first failure → set TTL
            redisTemplate.expire(key, props.getLockDuration());
        }
    }

    @Override
    public void resetFailures(AuthIdentityEntity identity) {
        redisTemplate.delete(key(identity.getAuthIdentityId()));
    }

    @Override
    public boolean isLocked(AuthIdentityEntity identity) {

        String key = key(identity.getAuthIdentityId());

        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }

        int attempts = Integer.parseInt(value);
        return attempts >= props.getMaxAttempts();
    }

    @Override
    public Instant lockedUntil(AuthIdentityEntity identity) {

        String key = key(identity.getAuthIdentityId());
        Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        if (seconds == null || seconds <= 0) {
            return null;
        }

        return Instant.now(clock).plusSeconds(seconds);
    }
}
