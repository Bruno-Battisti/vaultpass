package com.vaultpass.service;

import com.vaultpass.dto.category.CategoryRequest;
import com.vaultpass.dto.category.CategoryResponse;
import com.vaultpass.dto.mapper.CategoryMapper;
import com.vaultpass.entity.Category;
import com.vaultpass.entity.CategoryDefault;
import com.vaultpass.exception.DuplicateResourceException;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.CategoryRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public void seedDefaultCategories(UUID userId) {
        List<Category> defaults = Arrays.stream(CategoryDefault.values())
                .map(def -> Category.builder()
                        .userId(userId)
                        .name(def.name())
                        .defaultCategory(true)
                        .build())
                .toList();
        categoryRepository.saveAll(defaults);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId) {
        return categoryRepository.findAllByUserIdOrderByNameAsc(userId).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new DuplicateResourceException("Category already exists");
        }
        Category category = Category.builder()
                .userId(userId)
                .name(request.name())
                .build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID id, CategoryRequest request) {
        Category category = findOwnedOrThrow(id, userId);
        boolean nameChanged = !category.getName().equalsIgnoreCase(request.name());
        if (nameChanged && categoryRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new DuplicateResourceException("Category already exists");
        }
        category.setName(request.name());
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        categoryRepository.delete(findOwnedOrThrow(id, userId));
    }

    private Category findOwnedOrThrow(UUID id, UUID userId) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }
}
