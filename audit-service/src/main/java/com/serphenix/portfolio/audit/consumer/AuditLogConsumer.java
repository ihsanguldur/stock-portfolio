package com.serphenix.portfolio.audit.consumer;

import com.serphenix.portfolio.audit.entity.AuditLog;
import com.serphenix.portfolio.audit.event.AuditEvent;
import com.serphenix.portfolio.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogRepository auditLogRepository;

    @KafkaListener(topics = "audit-events", groupId = "portfolio-audit-group")
    public void consume(AuditEvent event) {
        AuditLog auditLog = AuditLog.create(
                event.userId(),
                event.action(),
                event.entityType(),
                event.entityId(),
                event.beforeState(),
                event.afterState()
        );

        auditLogRepository.save(auditLog);
    }
}
