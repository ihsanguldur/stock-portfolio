package com.serphenix.portfolio.audit.controller;

import com.serphenix.portfolio.audit.dto.response.AuditLogResponseDto;
import com.serphenix.portfolio.audit.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/audit-logs")
    public PagedModel<AuditLogResponseDto> auditLogs(Pageable pageable) {
        return adminService.findAuditLogs(pageable);
    }
}
