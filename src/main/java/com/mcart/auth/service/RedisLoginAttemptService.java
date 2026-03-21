package com.mcart.auth.service;

import com.mcart.auth.config.ConfigConstants;
import com.mcart.auth.entity.AuthIdentityEntity;
import com.mcart.auth.model.LoginAttemptProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLoginAttemptService implements LoginAttemptService {

    private static final String KEY_PREFIX = ConfigConstants.RedisKeys.LOGIN_FAILURE;

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

        try {
            int attempts = Integer.parseInt(value);
            return attempts >= props.getMaxAttempts();
        } catch (NumberFormatException e) {
            log.warn("Invalid login attempt count for key {}: {}", key, value);
            return false;
        }
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
