package com.serphenix.portfolio.audit.mapper;

import com.serphenix.portfolio.audit.dto.response.AuditLogResponseDto;
import com.serphenix.portfolio.audit.entity.AuditLog;

public class AuditLogMapper {
    public static AuditLogResponseDto toDto(AuditLog auditLog) {
        return new AuditLogResponseDto(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getBeforeState(),
                auditLog.getAfterState(),
                auditLog.getTimestamp()
        );
    }
}
