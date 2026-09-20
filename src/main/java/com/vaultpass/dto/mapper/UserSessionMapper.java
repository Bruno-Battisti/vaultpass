package com.vaultpass.dto.mapper;

import com.vaultpass.dto.security.UserSessionResponse;
import com.vaultpass.entity.UserSession;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserSessionMapper {

    UserSessionResponse toResponse(UserSession session);
}
