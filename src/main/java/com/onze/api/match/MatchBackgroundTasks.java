package com.onze.api.match;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MatchBackgroundTasks {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchBackgroundTasks.class);

    private final MatchLifecycleService lifecycleService;
    private final MatchNotificationProcessor notificationProcessor;

    public MatchBackgroundTasks(
            MatchLifecycleService lifecycleService,
            MatchNotificationProcessor notificationProcessor) {
        this.lifecycleService = lifecycleService;
        this.notificationProcessor = notificationProcessor;
    }

    @Scheduled(
            fixedDelayString = "${matches.processing.delay-ms:60000}",
            initialDelayString = "${matches.processing.initial-delay-ms:5000}")
    public void processMatchesAndNotifications() {
        try {
            lifecycleService.openDueAttendances();
            lifecycleService.scheduleDueAttendanceReminders();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not open due match attendances", exception);
        }

        try {
            notificationProcessor.processPending();
        } catch (RuntimeException exception) {
            LOGGER.error("Could not process match notifications", exception);
        }
    }
}
