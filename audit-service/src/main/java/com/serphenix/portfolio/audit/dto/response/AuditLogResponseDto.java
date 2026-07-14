package com.serphenix.portfolio.audit.dto.response;

import java.time.Instant;

public record AuditLogResponseDto(
        Long id,
        Long actorUserId,
        String action,
        String entityType,
        Long entityId,
        String beforeState,
        String afterState,
        Instant timestamp
) {
}
