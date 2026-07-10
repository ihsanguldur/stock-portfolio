package com.serphenix.portfolio.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
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
}
