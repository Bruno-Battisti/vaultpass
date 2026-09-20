package com.vaultpass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaultpass.dto.category.CategoryRequest;
import com.vaultpass.dto.category.CategoryResponse;
import com.vaultpass.dto.mapper.CategoryMapper;
import com.vaultpass.entity.Category;
import com.vaultpass.entity.CategoryDefault;
import com.vaultpass.exception.DuplicateResourceException;
import com.vaultpass.exception.ResourceNotFoundException;
import com.vaultpass.repository.CategoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void seedDefaultCategories_createsAllSevenDefaults() {
        UUID userId = UUID.randomUUID();

        categoryService.seedDefaultCategories(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Category>> captor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(captor.capture());
        List<Category> saved = captor.getValue();

        assertThat(saved).hasSize(CategoryDefault.values().length);
        assertThat(saved).allMatch(Category::isDefaultCategory);
        assertThat(saved).allMatch(c -> c.getUserId().equals(userId));
        assertThat(saved).extracting(Category::getName)
                .containsExactlyInAnyOrder("SOCIAL", "TRABALHO", "ESTUDOS", "FINANCEIRO", "JOGOS", "DESENVOLVIMENTO", "OUTROS");
    }

    @Test
    void create_duplicateName_throwsDuplicateResourceException() {
        UUID userId = UUID.randomUUID();
        CategoryRequest request = new CategoryRequest("Streaming");
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, "Streaming")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> categoryService.create(userId, request));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_success() {
        UUID userId = UUID.randomUUID();
        CategoryRequest request = new CategoryRequest("Streaming");
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(userId, "Streaming")).thenReturn(false);
        Category saved = Category.builder().id(UUID.randomUUID()).userId(userId).name("Streaming").build();
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);
        CategoryResponse expected = new CategoryResponse(saved.getId(), "Streaming", false, Instant.now());
        when(categoryMapper.toResponse(saved)).thenReturn(expected);

        CategoryResponse result = categoryService.create(userId, request);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void update_notOwnedByUser_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.update(userId, categoryId, new CategoryRequest("New name")));
    }

    @Test
    void delete_notOwnedByUser_throwsResourceNotFound() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findByIdAndUserId(categoryId, userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.delete(userId, categoryId));
        verify(categoryRepository, never()).delete(any());
    }
}
