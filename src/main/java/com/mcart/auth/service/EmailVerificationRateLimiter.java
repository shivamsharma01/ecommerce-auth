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

    @Value("${auth.verification.resend-rate-limit-max:10}")
    private int resendMaxPerHour;

    public void assertAllowed(UUID authIdentityId) {
        incrementAndAssert(ConfigConstants.RedisKeys.EMAIL_VERIFICATION + authIdentityId, maxPerHour,
                "Too many verification emails requested");
    }

    public void assertAllowedResend(UUID authIdentityId) {
        incrementAndAssert(ConfigConstants.RedisKeys.EMAIL_VERIFICATION_RESEND + authIdentityId,
                resendMaxPerHour,
                "Too many resend verification requests. Please try again later.");
    }

    private void incrementAndAssert(String key, int max, String message) {
        Long count = redis.opsForValue().increment(key);
        if (count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }
        if (count > max) {
            throw new TooManyRequestsException(message);
        }
    }
}
