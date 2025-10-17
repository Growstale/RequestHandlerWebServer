package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.WorkCategoryAlreadyExistsException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.WorkCategory;
import com.vodchyts.backend.feature.repository.ReactiveWorkCategoryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class WorkCategoryService {

    private final ReactiveWorkCategoryRepository workCategoryRepository;

    public WorkCategoryService(ReactiveWorkCategoryRepository workCategoryRepository) {
        this.workCategoryRepository = workCategoryRepository;
    }

    public Mono<PagedResponse<WorkCategoryResponse>> getAllWorkCategories(List<String> sort, int page, int size) {
        Flux<WorkCategory> categoriesFlux = workCategoryRepository.findAll();
        Flux<WorkCategoryResponse> categoryResponses = categoriesFlux.map(this::mapWorkCategoryToResponse);

        if (sort != null && !sort.isEmpty()) {
            Comparator<WorkCategoryResponse> comparator = buildComparator(sort);
            if (comparator != null) {
                categoryResponses = categoryResponses.sort(comparator);
            }
        }

        Mono<Long> countMono = workCategoryRepository.count();
        Mono<List<WorkCategoryResponse>> contentMono = categoryResponses
                .skip((long) page * size)
                .take(size)
                .collectList();

        return Mono.zip(contentMono, countMono)
                .map(tuple -> {
                    List<WorkCategoryResponse> content = tuple.getT1();
                    Long count = tuple.getT2();
                    int totalPages = (count == 0) ? 0 : (int) Math.ceil((double) count / size);
                    return new PagedResponse<>(content, page, count, totalPages);
                });
    }

    private Comparator<WorkCategoryResponse> buildComparator(List<String> sortParams) {
        Comparator<WorkCategoryResponse> finalComparator = null;
        for (String sortParam : sortParams) {
            String[] parts = sortParam.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;

            String field = parts[0];
            boolean isDescending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);

            Comparator<WorkCategoryResponse> currentComparator = switch (field) {
                case "workCategoryID" -> Comparator.comparing(WorkCategoryResponse::workCategoryID, Comparator.nullsLast(Comparator.naturalOrder()));
                case "workCategoryName" -> Comparator.comparing(WorkCategoryResponse::workCategoryName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                default -> null;
            };

            if (currentComparator != null) {
                if (isDescending) {
                    currentComparator = currentComparator.reversed();
                }
                finalComparator = (finalComparator == null) ? currentComparator : finalComparator.thenComparing(currentComparator);
            }
        }
        return finalComparator;
    }

    public Mono<WorkCategory> createWorkCategory(CreateWorkCategoryRequest request) {
        return workCategoryRepository.findByWorkCategoryName(request.workCategoryName())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new WorkCategoryAlreadyExistsException("Категория с названием '" + request.workCategoryName() + "' уже существует"));
                    }
                    WorkCategory category = new WorkCategory();
                    category.setWorkCategoryName(request.workCategoryName());
                    return workCategoryRepository.save(category);
                });
    }

    public Mono<WorkCategoryResponse> updateWorkCategory(Integer categoryId, UpdateWorkCategoryRequest request) {
        Mono<Void> uniquenessCheck = workCategoryRepository.findByWorkCategoryName(request.workCategoryName())
                .flatMap(existingCategory -> {
                    if (!Objects.equals(existingCategory.getWorkCategoryID(), categoryId)) {
                        return Mono.error(new WorkCategoryAlreadyExistsException("Категория с названием '" + request.workCategoryName() + "' уже существует"));
                    }
                    return Mono.empty();
                }).then();

        return uniquenessCheck
                .then(workCategoryRepository.findById(categoryId))
                .switchIfEmpty(Mono.error(new RuntimeException("Категория не найдена")))
                .flatMap(category -> {
                    category.setWorkCategoryName(request.workCategoryName());
                    return workCategoryRepository.save(category);
                })
                .map(this::mapWorkCategoryToResponse);
    }

    public Mono<Void> deleteWorkCategory(Integer categoryId) {
        return workCategoryRepository.deleteById(categoryId);
    }

    public WorkCategoryResponse mapWorkCategoryToResponse(WorkCategory category) {
        return new WorkCategoryResponse(
                category.getWorkCategoryID(),
                category.getWorkCategoryName()
        );
    }
}