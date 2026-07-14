package com.serphenix.portfolio.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @Column(name = "id")
    @EqualsAndHashCode.Include
    @ToString.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    @ToString.Include
    private Long actorUserId;

    @Column(name = "action")
    @ToString.Include
    private String action;

    @Column(name = "entity_type")
    @ToString.Include
    private String entityType;

    @Column(name = "entity_id")
    @ToString.Include
    private Long entityId;

    @Column(name = "before_state", columnDefinition = "TEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "TEXT")
    private String afterState;

    @Column(name = "timestamp")
    @ToString.Include
    private Instant timestamp;

    public static AuditLog create(Long actorUserId, String action, String entityType, Long entityId, String beforeState, String afterState) {
        AuditLog auditLog = new AuditLog();
        auditLog.actorUserId = actorUserId;
        auditLog.action = action;
        auditLog.entityType = entityType;
        auditLog.entityId = entityId;
        auditLog.beforeState = beforeState;
        auditLog.afterState = afterState;
        auditLog.timestamp = Instant.now();
        return auditLog;
    }
}
