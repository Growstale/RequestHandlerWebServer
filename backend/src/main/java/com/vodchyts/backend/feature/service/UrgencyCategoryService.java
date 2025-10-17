package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.dto.UpdateUrgencyCategoryRequest;
import com.vodchyts.backend.feature.dto.UrgencyCategoryResponse;
import com.vodchyts.backend.feature.repository.ReactiveUrgencyCategoryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UrgencyCategoryService {

    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;

    public UrgencyCategoryService(ReactiveUrgencyCategoryRepository urgencyCategoryRepository) {
        this.urgencyCategoryRepository = urgencyCategoryRepository;
    }

    public Flux<UrgencyCategoryResponse> getAllUrgencyCategories() {
        return urgencyCategoryRepository.findAll()
                .map(category -> new UrgencyCategoryResponse(
                        category.getUrgencyID(),
                        category.getUrgencyName(),
                        category.getDefaultDays()
                ));
    }

    public Mono<UrgencyCategoryResponse> updateUrgencyCategory(Integer urgencyId, UpdateUrgencyCategoryRequest request) {
        return urgencyCategoryRepository.findById(urgencyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Категория срочности не найдена")))
                .flatMap(category -> {
                    category.setDefaultDays(request.defaultDays());
                    return urgencyCategoryRepository.save(category);
                })
                .map(updatedCategory -> new UrgencyCategoryResponse(
                        updatedCategory.getUrgencyID(),
                        updatedCategory.getUrgencyName(),
                        updatedCategory.getDefaultDays()
                ));
    }
}