package com.serphenix.portfolio.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;

@Slf4j
public class CompensationHelper {

    public static void compensate(Runnable action, String description) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                action.run();
                log.info("Compensation succeeded on attempt {}: {}", attempt, description);
                return;
            } catch (RestClientException e) {
                log.error("Compensation attempt {}/3 failed: {} ({})", attempt, description, e.getMessage());
            }
        }

        log.error("Compensation exhausted after 3 attempts, MANUAL INTERVENTION REQUIRED: {}", description);
    }
}
