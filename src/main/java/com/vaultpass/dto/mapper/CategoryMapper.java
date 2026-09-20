package com.vaultpass.dto.mapper;

import com.vaultpass.dto.category.CategoryResponse;
import com.vaultpass.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(source = "defaultCategory", target = "isDefault")
    CategoryResponse toResponse(Category category);
}
