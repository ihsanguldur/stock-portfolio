package com.serphenix.portfolio.audit.event;

public record AuditEvent(
        Long userId,
        String action,
        String entityType,
        Long entityId,
        String beforeState,
        String afterState
) {
}
