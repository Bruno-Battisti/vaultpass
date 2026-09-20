package com.vaultpass.dto.mapper;

import com.vaultpass.dto.credential.CredentialResponse;
import com.vaultpass.entity.Credential;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CredentialMapper {

    @Mapping(target = "hasPassword", constant = "true")
    CredentialResponse toResponse(Credential credential);
}
