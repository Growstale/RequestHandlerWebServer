package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.WorkCategoryAlreadyExistsException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.WorkCategory;
import com.vodchyts.backend.feature.repository.ReactiveWorkCategoryRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class WorkCategoryService {

    private final ReactiveWorkCategoryRepository workCategoryRepository;
    private final DatabaseClient databaseClient;

    public WorkCategoryService(ReactiveWorkCategoryRepository workCategoryRepository, DatabaseClient databaseClient) {
        this.workCategoryRepository = workCategoryRepository;
        this.databaseClient = databaseClient;
    }

    public static final BiFunction<Row, RowMetadata, WorkCategoryResponse> WC_MAPPING_FUNCTION = (row, rowMetaData) -> new WorkCategoryResponse(
            row.get("WorkCategoryID", Integer.class),
            row.get("WorkCategoryName", String.class)
    );

    public Mono<PagedResponse<WorkCategoryResponse>> getAllWorkCategories(List<String> sort, int page, int size) {
        String sql = "SELECT WorkCategoryID, WorkCategoryName FROM WorkCategories";

        String countSql = "SELECT COUNT(*) FROM WorkCategories";
        Mono<Long> countMono = databaseClient.sql(countSql).map(row -> row.get(0, Long.class)).one();

        String sortedSql = sql + parseSortToSql(sort) + " OFFSET " + ((long) page * size) + " ROWS FETCH NEXT " + size + " ROWS ONLY";

        Flux<WorkCategoryResponse> contentFlux = databaseClient.sql(sortedSql)
                .map(WC_MAPPING_FUNCTION)
                .all();

        return Mono.zip(contentFlux.collectList(), countMono)
                .map(tuple -> {
                    List<WorkCategoryResponse> content = tuple.getT1();
                    Long count = tuple.getT2();
                    int totalPages = (count == 0) ? 0 : (int) Math.ceil((double) count / size);
                    return new PagedResponse<>(content, page, count, totalPages);
                });
    }

    private String parseSortToSql(List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return " ORDER BY WorkCategoryID ASC";
        }
        Map<String, String> columnMapping = Map.of(
                "workCategoryID", "WorkCategoryID",
                "workCategoryName", "WorkCategoryName"
        );

        String orders = sortParams.stream()
                .map(param -> {
                    String[] parts = param.split(",");
                    String field = parts[0];
                    String direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1])) ? "DESC" : "ASC";
                    String dbColumn = columnMapping.get(field);
                    if (dbColumn == null) return null;
                    return dbColumn + " " + direction;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));

        return orders.isEmpty() ? " ORDER BY WorkCategoryID ASC" : " ORDER BY " + orders;
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