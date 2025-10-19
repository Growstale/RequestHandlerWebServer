package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.*;
import com.vodchyts.backend.feature.repository.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

@Service
public class RequestService {

    private final R2dbcEntityTemplate template;
    private final ReactiveRequestRepository requestRepository;
    private final ReactiveShopRepository shopRepository;
    private final ReactiveWorkCategoryRepository workCategoryRepository;
    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveRequestCustomDayRepository customDayRepository;
    private final ReactiveRequestCommentRepository commentRepository;
    private final ReactiveRequestPhotoRepository photoRepository;

    public RequestService(R2dbcEntityTemplate template, ReactiveRequestRepository requestRepository, ReactiveShopRepository shopRepository, ReactiveWorkCategoryRepository workCategoryRepository, ReactiveUrgencyCategoryRepository urgencyCategoryRepository, ReactiveUserRepository userRepository, ReactiveRequestCustomDayRepository customDayRepository, ReactiveRequestCommentRepository commentRepository, ReactiveRequestPhotoRepository photoRepository) {
        this.template = template;
        this.requestRepository = requestRepository;
        this.shopRepository = shopRepository;
        this.workCategoryRepository = workCategoryRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.userRepository = userRepository;
        this.customDayRepository = customDayRepository;
        this.commentRepository = commentRepository;
        this.photoRepository = photoRepository;
    }

    public Mono<PagedResponse<RequestResponse>> getAllRequests(
            boolean archived, String searchTerm, Integer shopId, Integer workCategoryId,
            Integer urgencyId, Integer contractorId, String status, List<String> sort, int page, int size
    ) {
        List<String> statuses;
        if (archived) {
            statuses = List.of("Closed");
        } else if (status != null && !status.isBlank()) {
            // Если фильтр по статусу передан, используем его
            statuses = List.of(status);
        } else {
            // Иначе, по умолчанию показываем оба активных статуса
            statuses = List.of("In work", "Done");
        }

        Criteria criteria = where("Status").in(statuses);
        if (searchTerm != null && !searchTerm.isBlank()) {
            criteria = criteria.and(where("Description").like("%" + searchTerm + "%").ignoreCase(true));
        }
        if (shopId != null) criteria = criteria.and(where("ShopID").is(shopId));
        if (workCategoryId != null) criteria = criteria.and(where("WorkCategoryID").is(workCategoryId));
        if (urgencyId != null) criteria = criteria.and(where("UrgencyID").is(urgencyId));
        if (contractorId != null) criteria = criteria.and(where("AssignedContractorID").is(contractorId));

        Query query = query(criteria);

        Mono<Long> countMono = template.count(query, Request.class);
        Flux<Request> requestsFlux = template.select(query.offset((long) page * size).limit(size), Request.class);

        return requestsFlux.collectList()
                .flatMap(requests -> {
                    if (requests.isEmpty()) {
                        return Mono.just(new PagedResponse<>(List.of(), page, 0L, 0));
                    }
                    return enrichRequests(requests)
                            .collectList()
                            .flatMap(enrichedRequests -> countMono.map(total -> {
                                List<RequestResponse> sortedRequests = sortRequests(enrichedRequests, sort);
                                int totalPages = (total == 0) ? 0 : (int) Math.ceil((double) total / size);
                                return new PagedResponse<>(sortedRequests, page, total, totalPages);
                            }));
                });
    }

    public Mono<RequestResponse> createAndEnrichRequest(CreateRequestRequest dto, Integer createdByUserId) {
        return createRequest(dto, createdByUserId)
                .flatMap(request -> enrichRequests(List.of(request)).single());
    }

    protected Mono<Request> createRequest(CreateRequestRequest dto, Integer createdByUserId) {
        return urgencyCategoryRepository.findById(dto.urgencyID())
                .flatMap(urgency -> {
                    Request request = new Request();
                    request.setDescription(dto.description());
                    request.setShopID(dto.shopID());
                    request.setWorkCategoryID(dto.workCategoryID());
                    request.setUrgencyID(dto.urgencyID());
                    request.setAssignedContractorID(dto.assignedContractorID());
                    request.setCreatedByUserID(createdByUserId);
                    request.setStatus("In work");
                    request.setCreatedAt(LocalDateTime.now());
                    request.setIsOverdue(false);

                    Mono<Request> savedRequestMono = requestRepository.save(request);

                    if ("Customizable".equalsIgnoreCase(urgency.getUrgencyName()) && dto.customDays() != null) {
                        return savedRequestMono.flatMap(savedRequest -> {
                            RequestCustomDay customDay = new RequestCustomDay();
                            customDay.setRequestID(savedRequest.getRequestID());
                            customDay.setDays(dto.customDays());
                            return customDayRepository.save(customDay).thenReturn(savedRequest);
                        });
                    }
                    return savedRequestMono;
                });
    }

    public Mono<RequestResponse> updateAndEnrichRequest(Integer requestId, UpdateRequestRequest dto) {
        return updateRequest(requestId, dto)
                .flatMap(request -> enrichRequests(List.of(request)).single());
    }

    protected Mono<Request> updateRequest(Integer requestId, UpdateRequestRequest dto) {
        return requestRepository.findById(requestId)
                .zipWith(urgencyCategoryRepository.findById(dto.urgencyID()))
                .flatMap(tuple -> {
                    Request request = tuple.getT1();
                    UrgencyCategory newUrgency = tuple.getT2();

                    request.setDescription(dto.description());
                    request.setShopID(dto.shopID());
                    request.setWorkCategoryID(dto.workCategoryID());
                    request.setUrgencyID(dto.urgencyID());
                    request.setAssignedContractorID(dto.assignedContractorID());

                    // Логика смены статуса
                    if (!request.getStatus().equals(dto.status()) && "Closed".equalsIgnoreCase(dto.status())) {
                        request.setClosedAt(LocalDateTime.now());
                    }
                    request.setStatus(dto.status());

                    Mono<Request> updatedRequestMono = requestRepository.save(request);

                    boolean isCustomizable = "Customizable".equalsIgnoreCase(newUrgency.getUrgencyName());
                    Mono<Void> customDaysLogic = customDayRepository.findByRequestID(requestId)
                            // 1. Обрабатываем случай, когда запись найдена
                            .flatMap(existingCustomDay -> {
                                if (isCustomizable && dto.customDays() != null) {
                                    // Если нужно обновить, обновляем и завершаем
                                    existingCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(existingCustomDay);
                                } else {
                                    // Если нужно удалить, удаляем и завершаем
                                    return customDayRepository.delete(existingCustomDay).then(Mono.empty());
                                }
                            })
                            // 2. Обрабатываем случай, когда запись НЕ найдена
                            .switchIfEmpty(Mono.defer(() -> {
                                if (isCustomizable && dto.customDays() != null) {
                                    // Если нужно создать, создаем и завершаем
                                    RequestCustomDay newCustomDay = new RequestCustomDay();
                                    newCustomDay.setRequestID(requestId);
                                    newCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(newCustomDay);
                                }
                                // Если ничего делать не нужно, просто завершаем
                                return Mono.empty();
                            }))
                            .then(); // 3. В самом конце превращаем результат в Mono<Void>

                    return customDaysLogic.then(updatedRequestMono);
                });
    }

    public Mono<Void> deleteRequest(Integer requestId) {
        return requestRepository.deleteById(requestId);
    }

    public Flux<RequestResponse> enrichRequests(List<Request> requests) {
        if (requests.isEmpty()) {
            return Flux.empty();
        }
        List<Integer> shopIds = requests.stream().map(Request::getShopID).filter(Objects::nonNull).distinct().toList();
        List<Integer> workCategoryIds = requests.stream().map(Request::getWorkCategoryID).filter(Objects::nonNull).distinct().toList();
        List<Integer> urgencyIds = requests.stream().map(Request::getUrgencyID).filter(Objects::nonNull).distinct().toList();
        List<Integer> contractorIds = requests.stream().map(Request::getAssignedContractorID).filter(Objects::nonNull).distinct().toList();
        List<Integer> requestIds = requests.stream().map(Request::getRequestID).toList();

        Mono<Map<Integer, Shop>> shopsMapMono = shopRepository.findAllById(shopIds).collectMap(Shop::getShopID);
        Mono<Map<Integer, WorkCategory>> workCategoriesMapMono = workCategoryRepository.findAllById(workCategoryIds).collectMap(WorkCategory::getWorkCategoryID);
        Mono<Map<Integer, UrgencyCategory>> urgencyCategoriesMapMono = urgencyCategoryRepository.findAllById(urgencyIds).collectMap(UrgencyCategory::getUrgencyID);
        Mono<Map<Integer, User>> contractorsMapMono = userRepository.findAllById(contractorIds).collectMap(User::getUserID);
        Mono<Map<Integer, RequestCustomDay>> customDaysMapMono = customDayRepository.findByRequestIDIn(requestIds).collectMap(RequestCustomDay::getRequestID);
        Mono<Map<Integer, Long>> commentsCountMapMono = getCommentCounts(requestIds);
        Mono<Map<Integer, Long>> photosCountMapMono = getPhotoCounts(requestIds);

        return Mono.zip(shopsMapMono, workCategoriesMapMono, urgencyCategoriesMapMono, contractorsMapMono, customDaysMapMono, commentsCountMapMono, photosCountMapMono)
                .flatMapMany(tuple -> {
                    Map<Integer, Shop> shops = tuple.getT1();
                    Map<Integer, WorkCategory> workCategories = tuple.getT2();
                    Map<Integer, UrgencyCategory> urgencyCategories = tuple.getT3();
                    Map<Integer, User> contractors = tuple.getT4();
                    Map<Integer, RequestCustomDay> customDays = tuple.getT5();
                    Map<Integer, Long> commentsCounts = tuple.getT6();
                    Map<Integer, Long> photosCounts = tuple.getT7();

                    return Flux.fromStream(requests.stream().map(req -> {
                        Shop shop = shops.get(req.getShopID());
                        WorkCategory workCategory = workCategories.get(req.getWorkCategoryID());
                        UrgencyCategory urgency = urgencyCategories.get(req.getUrgencyID());
                        User contractor = contractors.get(req.getAssignedContractorID());

                        Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                ? customDays.getOrDefault(req.getRequestID(), new RequestCustomDay()).getDays()
                                : urgency.getDefaultDays();

                        Integer daysRemaining = null;
                        if (daysForTask != null && !"Closed".equals(req.getStatus())) {
                            LocalDateTime deadline = req.getCreatedAt().plusDays(daysForTask);
                            daysRemaining = (int) Duration.between(LocalDateTime.now(), deadline).toDays();
                        }

                        return new RequestResponse(
                                req.getRequestID(),
                                req.getDescription(),
                                shop != null ? shop.getShopName() : "N/A",
                                req.getShopID(),
                                workCategory != null ? workCategory.getWorkCategoryName() : "N/A",
                                req.getWorkCategoryID(),
                                urgency.getUrgencyName(),
                                req.getUrgencyID(),
                                contractor != null ? contractor.getLogin() : null,
                                req.getAssignedContractorID(),
                                req.getStatus(),
                                req.getCreatedAt(),
                                daysRemaining,
                                commentsCounts.getOrDefault(req.getRequestID(), 0L),
                                photosCounts.getOrDefault(req.getRequestID(), 0L)
                        );
                    }));
                });
    }

    public Flux<Integer> getPhotoIdsForRequest(Integer requestId) {
        return photoRepository.findByRequestID(requestId)
                .map(RequestPhoto::getRequestPhotoID);
    }

    public Mono<byte[]> getPhotoById(Integer photoId) {
        return photoRepository.findById(photoId)
                .map(RequestPhoto::getImageData);
    }

    private List<RequestResponse> sortRequests(List<RequestResponse> requests, List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return requests;
        }

        Comparator<RequestResponse> finalComparator = null;
        for (String sortParam : sortParams) {
            String[] parts = sortParam.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;

            String field = parts[0];
            boolean isDescending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);

            Comparator<RequestResponse> currentComparator = switch (field) {
                case "requestID" -> Comparator.comparing(RequestResponse::requestID);
                case "description" -> Comparator.comparing(RequestResponse::description, String.CASE_INSENSITIVE_ORDER);                case "shopName" -> Comparator.comparing(RequestResponse::shopName, String.CASE_INSENSITIVE_ORDER);
                case "workCategoryName" -> Comparator.comparing(RequestResponse::workCategoryName, String.CASE_INSENSITIVE_ORDER);
                case "urgencyName" -> Comparator.comparing(RequestResponse::urgencyName, String.CASE_INSENSITIVE_ORDER);
                case "assignedContractorName" -> Comparator.comparing(RequestResponse::assignedContractorName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case "status" -> Comparator.comparing(RequestResponse::status, String.CASE_INSENSITIVE_ORDER);
                case "createdAt" -> Comparator.comparing(RequestResponse::createdAt);
                case "daysRemaining" -> Comparator.comparing(RequestResponse::daysRemaining, Comparator.nullsLast(Comparator.naturalOrder()));
                default -> null;
            };

            if (currentComparator != null) {
                if (isDescending) currentComparator = currentComparator.reversed();
                finalComparator = finalComparator == null ? currentComparator : finalComparator.thenComparing(currentComparator);
            }
        }

        return finalComparator != null ? requests.stream().sorted(finalComparator).toList() : requests;
    }
    public Flux<CommentResponse> getCommentsForRequest(Integer requestId) {
        return commentRepository.findByRequestIDOrderByCreatedAtAsc(requestId)
                .flatMap(comment -> userRepository.findById(comment.getUserID())
                        .map(user -> new CommentResponse(
                                comment.getCommentID(),
                                comment.getRequestID(),
                                user.getLogin(),
                                comment.getCommentText(),
                                comment.getCreatedAt()
                        ))
                );
    }

    public Mono<CommentResponse> addCommentToRequest(Integer requestId, CreateCommentRequest dto, Integer userId) {
        // 1. Находим заявку по ID. Если не найдена - ошибка.
        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .flatMap(request -> {
                    // 2. Проверяем статус заявки. Если "Closed", выбрасываем исключение.
                    if ("Closed".equalsIgnoreCase(request.getStatus())) {
                        return Mono.error(new OperationNotAllowedException("Нельзя комментировать закрытую заявку."));
                    }

                    // 3. Если статус позволяет, создаем и настраиваем новый комментарий.
                    RequestComment newComment = new RequestComment();
                    newComment.setRequestID(requestId);
                    newComment.setUserID(userId);
                    newComment.setCommentText(dto.commentText());
                    newComment.setCreatedAt(LocalDateTime.now());

                    // 4. Сохраняем комментарий в репозитории.
                    return commentRepository.save(newComment);
                })
                // 5. После успешного сохранения "обогащаем" комментарий данными пользователя (его логином).
                .flatMap(savedComment -> userRepository.findById(userId)
                        .map(user -> new CommentResponse(
                                savedComment.getCommentID(),
                                savedComment.getRequestID(),
                                user.getLogin(),
                                savedComment.getCommentText(),
                                savedComment.getCreatedAt()
                        ))
                );
    }


    public Flux<byte[]> getPhotosForRequest(Integer requestId) {
        return photoRepository.findByRequestID(requestId)
                .map(RequestPhoto::getImageData);
    }

    public Mono<Void> addPhotosToRequest(Integer requestId, Flux<byte[]> imagesData) { // Принимает Flux<byte[]>
        return requestRepository.findById(requestId)
                .flatMap(request -> {
                    if ("Closed".equalsIgnoreCase(request.getStatus())) {
                        return Mono.error(new OperationNotAllowedException("Нельзя добавлять фото в закрытую заявку."));
                    }
                    return imagesData
                            .flatMap(imageData -> { // flatMap для каждого файла в потоке
                                RequestPhoto photo = new RequestPhoto();
                                photo.setRequestID(requestId);
                                photo.setImageData(imageData);
                                return photoRepository.save(photo);
                            })
                            .then();
                }).then();
    }

    public Mono<RequestResponse> restoreRequest(Integer requestId) {
        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .flatMap(request -> {
                    if (!"Closed".equalsIgnoreCase(request.getStatus())) {
                        return Mono.error(new OperationNotAllowedException("Можно восстановить только закрытую заявку."));
                    }
                    request.setStatus("In work");
                    request.setClosedAt(null); // Убираем дату закрытия
                    return requestRepository.save(request);
                })
                .flatMap(savedRequest -> enrichRequests(List.of(savedRequest)).single());
    }


    private Mono<Map<Integer, Long>> getCommentCounts(List<Integer> requestIds) {
        if (requestIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        // 1. Создаем критерий: RequestID должен быть в нашем списке
        Criteria criteria = where("RequestID").in(requestIds);

        // 2. Создаем запрос на основе критерия
        Query query = query(criteria);

        // 3. Выполняем запрос через R2dbcEntityTemplate
        return template.select(query, RequestComment.class) // Получаем Flux<RequestComment>
                // 4. Группируем комментарии по RequestID
                .collect(Collectors.groupingBy(
                        RequestComment::getRequestID,
                        // 5. Для каждой группы считаем количество элементов
                        Collectors.counting()
                ))
                .defaultIfEmpty(Map.of()); // Если результат пустой, возвращаем пустую Map
    }
    // ^^^

    // vvv ЗАМЕНИТЕ СТАРЫЙ МЕТОД getPhotoCounts НА ЭТОТ vvv
    private Mono<Map<Integer, Long>> getPhotoCounts(List<Integer> requestIds) {
        if (requestIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        Criteria criteria = where("RequestID").in(requestIds);
        Query query = query(criteria);

        return template.select(query, RequestPhoto.class) // Получаем Flux<RequestPhoto>
                .collect(Collectors.groupingBy(
                        RequestPhoto::getRequestID,
                        Collectors.counting()
                ))
                .defaultIfEmpty(Map.of());
    }

    public Mono<Void> deletePhoto(Integer photoId) {
        return photoRepository.findById(photoId)
                .switchIfEmpty(Mono.error(new RuntimeException("Фото с ID " + photoId + " не найдено")))
                .flatMap(photo -> requestRepository.findById(photo.getRequestID())
                        .flatMap(request -> {
                            if ("Closed".equalsIgnoreCase(request.getStatus())) {
                                return Mono.error(new OperationNotAllowedException("Нельзя удалять фото из закрытой заявки."));
                            }
                            return photoRepository.deleteById(photoId);
                        })
                );
    }
}