package com.serphenix.portfolio.audit.service;

import com.serphenix.portfolio.audit.dto.response.AuditLogResponseDto;
import com.serphenix.portfolio.audit.mapper.AuditLogMapper;
import com.serphenix.portfolio.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuditLogRepository auditLogRepository;

    public PagedModel<AuditLogResponseDto> findAuditLogs(Pageable pageable) {
        return new PagedModel<>(auditLogRepository.findAll(pageable).map(AuditLogMapper::toDto));
    }
}
