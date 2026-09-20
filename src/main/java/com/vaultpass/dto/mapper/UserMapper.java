package com.vaultpass.dto.mapper;

import com.vaultpass.dto.auth.UserSummaryResponse;
import com.vaultpass.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserSummaryResponse toSummary(User user);
}
