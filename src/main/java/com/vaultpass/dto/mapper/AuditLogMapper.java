package com.vaultpass.dto.mapper;

import com.vaultpass.dto.security.AuditLogResponse;
import com.vaultpass.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
