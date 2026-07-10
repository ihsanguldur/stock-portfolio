package com.serphenix.portfolio.service;

import com.serphenix.portfolio.dto.response.AuditLogResponseDto;
import com.serphenix.portfolio.mapper.AuditLogMapper;
import com.serphenix.portfolio.repository.AuditLogRepository;
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
