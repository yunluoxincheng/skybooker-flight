package com.skybooker.waitlist.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaitlistCleanupScheduler {

    private final WaitlistService waitlistService;

    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredPending() {
        try {
            waitlistService.cleanupExpiredPending();
        } catch (Exception e) {
            log.warn("Waitlist expired-pending cleanup failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanupUnfulfillableWaiting() {
        try {
            waitlistService.cleanupUnfulfillableWaiting();
        } catch (Exception e) {
            log.warn("Waitlist unfulfillable cleanup failed: {}", e.getMessage());
        }
    }
}
