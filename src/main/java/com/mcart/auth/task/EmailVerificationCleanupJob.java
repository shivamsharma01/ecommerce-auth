package com.mcart.auth.task;

import com.mcart.auth.repository.EmailVerificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationCleanupJob {

    private final EmailVerificationRepository emailVerificationRepo;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void deleteExpiredTokens() {

        Instant now = Instant.now(clock);
        long deleted = emailVerificationRepo.deleteByExpiresAtBefore(now);

        if (deleted > 0) {
            log.info("Deleted {} expired email verification tokens", deleted);
        }
    }
}
