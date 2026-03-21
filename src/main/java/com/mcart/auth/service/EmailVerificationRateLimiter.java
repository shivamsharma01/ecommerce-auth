package com.mcart.auth.service;

import com.mcart.auth.config.ConfigConstants;
import com.mcart.auth.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailVerificationRateLimiter {

    private final StringRedisTemplate redis;

    @Value("${auth.verification.rate-limit-max:3}")
    private int maxPerHour;

    public void assertAllowed(UUID authIdentityId) {

        String key = ConfigConstants.RedisKeys.EMAIL_VERIFICATION + authIdentityId;

        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }

        if (count > maxPerHour) {
            throw new TooManyRequestsException(
                    "Too many verification emails requested"
            );
        }
    }
}
