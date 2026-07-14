package com.serphenix.portfolio.audit;

import com.serphenix.portfolio.audit.event.AuditEvent;
import com.serphenix.portfolio.dto.response.Identifiable;
import com.serphenix.portfolio.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JsonMapper jsonMapper;

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        JwtPrincipal principal = (JwtPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Object request = joinPoint.getArgs()[1];

        Object result = joinPoint.proceed();

        try {
            Long entityId = result instanceof Identifiable ? ((Identifiable) result).id() : null;

            AuditEvent event = new AuditEvent(
                    principal.userId(),
                    audited.action(),
                    audited.entityType(),
                    entityId,
                    jsonMapper.writeValueAsString(request),
                    jsonMapper.writeValueAsString(result)
            );

            kafkaTemplate.send("audit-events", event);
        } catch (Exception e) {
            log.error("Failed to publish audit event for action {}: {}", audited.action(), e.getMessage());
        }

        return result;
    }
}
