package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.*;
import com.vodchyts.backend.feature.repository.*;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.vodchyts.backend.exception.UserNotFoundException;

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
    private final ReactiveRoleRepository roleRepository;

    public RequestService(R2dbcEntityTemplate template, ReactiveRequestRepository requestRepository, ReactiveShopRepository shopRepository, ReactiveWorkCategoryRepository workCategoryRepository, ReactiveUrgencyCategoryRepository urgencyCategoryRepository, ReactiveUserRepository userRepository, ReactiveRequestCustomDayRepository customDayRepository, ReactiveRequestCommentRepository commentRepository, ReactiveRequestPhotoRepository photoRepository, ReactiveRoleRepository roleRepository) {
        this.template = template;
        this.requestRepository = requestRepository;
        this.shopRepository = shopRepository;
        this.workCategoryRepository = workCategoryRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.userRepository = userRepository;
        this.customDayRepository = customDayRepository;
        this.commentRepository = commentRepository;
        this.photoRepository = photoRepository;
        this.roleRepository = roleRepository;
    }

    public Mono<PagedResponse<RequestResponse>> getAllRequests(
            boolean archived, String searchTerm, Integer shopId, Integer workCategoryId,
            Integer urgencyId, Integer contractorId, String status, Boolean overdue, List<String> sort, int page, int size,
            String username // <-- Добавляем имя текущего пользователя
    ) {
        return userRepository.findByLogin(username)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Текущий пользователь не найден")))
                .flatMap(user -> roleRepository.findById(user.getRoleID())
                        .flatMap(role -> {
                            List<String> statuses;
                            if (archived) {
                                statuses = List.of("Closed");
                            } else if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                                statuses = List.of(status);
                            } else {
                                statuses = List.of("In work", "Done");
                            }

                            Criteria criteria = where("Status").in(statuses);

                            if (overdue != null && overdue) {
                                criteria = criteria.and(where("IsOverdue").is(true));
                            }

                            // Применяем общие фильтры, если они есть
                            if (searchTerm != null && !searchTerm.isBlank()) {
                                criteria = criteria.and(where("Description").like("%" + searchTerm + "%").ignoreCase(true));
                            }
                            if (workCategoryId != null) criteria = criteria.and(where("WorkCategoryID").is(workCategoryId));
                            if (urgencyId != null) criteria = criteria.and(where("UrgencyID").is(urgencyId));

                            Mono<Criteria> roleBasedCriteriaMono = Mono.just(criteria);
                            String userRole = role.getRoleName();

                            if ("RetailAdmin".equals(userRole)) {
                                if (shopId != null) criteria = criteria.and(where("ShopID").is(shopId));
                                if (contractorId != null) criteria = criteria.and(where("AssignedContractorID").is(contractorId));
                                roleBasedCriteriaMono = Mono.just(criteria);
                            } else if ("Contractor".equals(userRole)) {
                                criteria = criteria.and(where("AssignedContractorID").is(user.getUserID()));
                                roleBasedCriteriaMono = Mono.just(criteria);
                            } else if ("StoreManager".equals(userRole)) {
                                Criteria finalCriteria1 = criteria;
                                roleBasedCriteriaMono = shopRepository.findAllByUserID(user.getUserID())
                                        .map(Shop::getShopID)
                                        .collectList()
                                        .map(shopIds -> {
                                            if (shopIds.isEmpty()) {
                                                return where("RequestID").isNull();
                                            }
                                            return finalCriteria1.and(where("ShopID").in(shopIds));
                                        });
                            }

                            return roleBasedCriteriaMono.flatMap(finalCriteria -> {
                                Query query = query(finalCriteria);
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
                            });
                        }));
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

                    if (!request.getStatus().equals(dto.status()) && "Closed".equalsIgnoreCase(dto.status())) {
                        request.setClosedAt(LocalDateTime.now());
                    }
                    request.setStatus(dto.status());

                    if (!"In work".equalsIgnoreCase(request.getStatus())) {
                        request.setIsOverdue(false);
                    } else {
                        Integer daysForTask = "Customizable".equalsIgnoreCase(newUrgency.getUrgencyName())
                                ? dto.customDays()
                                : newUrgency.getDefaultDays();

                        if (daysForTask != null) {
                            LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                            request.setIsOverdue(LocalDateTime.now().isAfter(deadline));
                        } else {
                            request.setIsOverdue(false);
                        }
                    }

                    Mono<Request> updatedRequestMono = requestRepository.save(request);

                    boolean isCustomizable = "Customizable".equalsIgnoreCase(newUrgency.getUrgencyName());
                    Mono<Void> customDaysLogic = customDayRepository.findByRequestID(requestId)
                            .flatMap(existingCustomDay -> {
                                if (isCustomizable && dto.customDays() != null) {
                                    // Если нужно обновить, обновляем и завершаем
                                    existingCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(existingCustomDay);
                                } else {
                                    return customDayRepository.delete(existingCustomDay).then(Mono.empty());
                                }
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                if (isCustomizable && dto.customDays() != null) {
                                    RequestCustomDay newCustomDay = new RequestCustomDay();
                                    newCustomDay.setRequestID(requestId);
                                    newCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(newCustomDay);
                                }
                                return Mono.empty();
                            }))
                            .then();

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
                                req.getClosedAt(),
                                daysRemaining,
                                daysForTask,
                                req.getIsOverdue(),
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
        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .zipWith(userRepository.findById(userId))
                .flatMap(tuple -> {
                    Request request = tuple.getT1();
                    User user = tuple.getT2();

                    return canUserModify(request, user).flatMap(canModify -> {
                        if (!canModify) {
                            return Mono.error(new OperationNotAllowedException("У вас нет прав для комментирования этой заявки."));
                        }
                        if ("Closed".equalsIgnoreCase(request.getStatus())) {
                            return Mono.error(new OperationNotAllowedException("Нельзя комментировать закрытую заявку."));
                        }

                        RequestComment newComment = new RequestComment();
                        newComment.setRequestID(requestId);
                        newComment.setUserID(userId);
                        newComment.setCommentText(dto.commentText());
                        newComment.setCreatedAt(LocalDateTime.now());

                        return commentRepository.save(newComment);
                    });
                })
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

    public Mono<Void> addPhotosToRequest(Integer requestId, Flux<FilePart> filePartFlux, Integer userId) {
        Flux<byte[]> imagesDataFlux = filePartFlux.flatMap(filePart ->
                filePart.content()
                        .collectList()
                        .mapNotNull(dataBuffers -> {
                            if (dataBuffers.isEmpty()) return null;
                            DataBuffer joinedBuffer = dataBuffers.getFirst().factory().join(dataBuffers);
                            dataBuffers.forEach(DataBufferUtils::release);
                            return joinedBuffer;
                        })
                        .filter(Objects::nonNull)
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
        );

        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .zipWith(userRepository.findById(userId))
                .flatMap(tuple -> {
                    Request request = tuple.getT1();
                    User user = tuple.getT2();

                    return canUserModify(request, user).flatMap(canModify -> {
                        if (!canModify) {
                            return Mono.error(new OperationNotAllowedException("У вас нет прав..."));
                        }
                        if ("Closed".equalsIgnoreCase(request.getStatus())) {
                            return Mono.error(new OperationNotAllowedException("Нельзя добавлять фото..."));
                        }
                        // Используем наш преобразованный поток
                        return imagesDataFlux
                                .flatMap(imageData -> {
                                    RequestPhoto photo = new RequestPhoto();
                                    photo.setRequestID(requestId);
                                    photo.setImageData(imageData);
                                    return photoRepository.save(photo);
                                })
                                .then();
                    });
                }).then();
    }

    public Mono<RequestResponse> completeRequest(Integer requestId, Integer contractorId) {
        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .flatMap(request -> {
                    if (!Objects.equals(request.getAssignedContractorID(), contractorId)) {
                        return Mono.error(new OperationNotAllowedException("Вы не являетесь исполнителем по этой заявке."));
                    }
                    if (!"In work".equalsIgnoreCase(request.getStatus())) {
                        return Mono.error(new OperationNotAllowedException("Заявку можно завершить только из статуса 'В работе'."));
                    }
                    request.setStatus("Done");
                    return requestRepository.save(request);
                })
                .flatMap(savedRequest -> enrichRequests(List.of(savedRequest)).single());
    }

    private Mono<Boolean> canUserModify(Request request, User user) {
        return roleRepository.findById(user.getRoleID()).flatMap(role -> {
            String roleName = role.getRoleName();
            if ("RetailAdmin".equals(roleName)) {
                return Mono.just(true);
            }
            if ("Contractor".equals(roleName) && Objects.equals(user.getUserID(), request.getAssignedContractorID())) {
                return Mono.just(true);
            }
            return Mono.just(false);
        });
    }



    public Mono<RequestResponse> restoreRequest(Integer requestId) {
        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .flatMap(request -> {
                    if (!"Closed".equalsIgnoreCase(request.getStatus())) {
                        return Mono.error(new OperationNotAllowedException("Можно восстановить только закрытую заявку."));
                    }
                    request.setStatus("In work");
                    request.setClosedAt(null);
                    return requestRepository.save(request);
                })
                .flatMap(savedRequest -> enrichRequests(List.of(savedRequest)).single());
    }


    private Mono<Map<Integer, Long>> getCommentCounts(List<Integer> requestIds) {
        if (requestIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        Criteria criteria = where("RequestID").in(requestIds);
        Query query = query(criteria);

        return template.select(query, RequestComment.class)
                .collect(Collectors.groupingBy(
                        RequestComment::getRequestID,
                        Collectors.counting()
                ))
                .defaultIfEmpty(Map.of());
    }

    private Mono<Map<Integer, Long>> getPhotoCounts(List<Integer> requestIds) {
        if (requestIds.isEmpty()) {
            return Mono.just(Map.of());
        }

        Criteria criteria = where("RequestID").in(requestIds);
        Query query = query(criteria);

        return template.select(query, RequestPhoto.class)
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