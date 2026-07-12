package com.serphenix.portfolio.audit;

import com.serphenix.portfolio.dto.response.Identifiable;
import com.serphenix.portfolio.audit.entity.AuditLog;
import com.serphenix.portfolio.auth.entity.User;
import com.serphenix.portfolio.exception.InvalidCredentialsException;
import com.serphenix.portfolio.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final UserRepository userRepository;
    private final AuditLogWriter auditLogWriter;
    private final JsonMapper jsonMapper;

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Object request = joinPoint.getArgs()[1];

        Object result = joinPoint.proceed();

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new InvalidCredentialsException("User not found"));

            Long entityId = result instanceof Identifiable ? ((Identifiable) result).id() : null;

            AuditLog auditLog = AuditLog.create(
                    user.getId(),
                    audited.action(),
                    audited.entityType(),
                    entityId,
                    jsonMapper.writeValueAsString(request),
                    jsonMapper.writeValueAsString(result)
            );

            auditLogWriter.write(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for action {}: {}", audited.action(), e.getMessage());
        }

        return result;
    }
}

