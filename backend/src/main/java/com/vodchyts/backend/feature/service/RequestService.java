package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.*;
import com.vodchyts.backend.feature.entity.*;
import com.vodchyts.backend.feature.repository.*;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class RequestService {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;
    private final ReactiveRequestRepository requestRepository;
    private final ReactiveRequestCustomDayRepository customDayRepository;
    private final ReactiveRequestCommentRepository commentRepository;
    private final ReactiveRequestPhotoRepository photoRepository;
    private final ReactiveRoleRepository roleRepository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveShopRepository shopRepository;


    public RequestService(R2dbcEntityTemplate template, DatabaseClient databaseClient, ReactiveRequestRepository requestRepository, ReactiveRequestCustomDayRepository customDayRepository, ReactiveRequestCommentRepository commentRepository, ReactiveRequestPhotoRepository photoRepository, ReactiveRoleRepository roleRepository, ReactiveUserRepository userRepository, ReactiveShopRepository shopRepository) {
        this.template = template;
        this.databaseClient = databaseClient;
        this.requestRepository = requestRepository;
        this.customDayRepository = customDayRepository;
        this.commentRepository = commentRepository;
        this.photoRepository = photoRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
    }


    public static final BiFunction<Row, RowMetadata, RequestResponse> MAPPING_FUNCTION = (row, rowMetaData) -> new RequestResponse(
            row.get("RequestID", Integer.class),
            row.get("Description", String.class),
            row.get("ShopName", String.class),
            row.get("ShopID", Integer.class),
            row.get("WorkCategoryName", String.class),
            row.get("WorkCategoryID", Integer.class),
            row.get("UrgencyName", String.class),
            row.get("UrgencyID", Integer.class),
            row.get("AssignedContractorName", String.class),
            row.get("AssignedContractorID", Integer.class),
            row.get("Status", String.class),
            row.get("CreatedAt", LocalDateTime.class),
            row.get("ClosedAt", LocalDateTime.class),
            null,
            row.get("DaysForTask", Integer.class),
            row.get("IsOverdue", Boolean.class),
            Optional.ofNullable(row.get("CommentCount", Long.class)).orElse(0L),
            Optional.ofNullable(row.get("PhotoCount", Long.class)).orElse(0L)
    );

    public Mono<PagedResponse<RequestResponse>> getAllRequests(
            boolean archived, String searchTerm, Integer shopId, Integer workCategoryId,
            Integer urgencyId, Integer contractorId, String status, Boolean overdue, List<String> sort, int page, int size,
            String username
    ) {
        return userRepository.findByLogin(username)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Текущий пользователь не найден")))
                .flatMap(user -> roleRepository.findById(user.getRoleID())
                        .flatMap(role -> {
                            StringBuilder sqlBuilder = new StringBuilder(
                                    "SELECT r.RequestID, r.Description, r.ShopID, r.WorkCategoryID, r.UrgencyID, r.AssignedContractorID, r.Status, r.CreatedAt, r.ClosedAt, r.IsOverdue, " +
                                            "s.ShopName, wc.WorkCategoryName, uc.UrgencyName, u.Login as AssignedContractorName, " +
                                            "CASE WHEN uc.UrgencyName = 'Customizable' THEN rcd.Days ELSE uc.DefaultDays END as DaysForTask, " +
                                            "(SELECT COUNT(*) FROM RequestComments rc WHERE rc.RequestID = r.RequestID) as CommentCount, " +
                                            "(SELECT COUNT(*) FROM RequestPhotos rp WHERE rp.RequestID = r.RequestID) as PhotoCount " +
                                            "FROM Requests r " +
                                            "LEFT JOIN Shops s ON r.ShopID = s.ShopID " +
                                            "LEFT JOIN WorkCategories wc ON r.WorkCategoryID = wc.WorkCategoryID " +
                                            "LEFT JOIN UrgencyCategories uc ON r.UrgencyID = uc.UrgencyID " +
                                            "LEFT JOIN Users u ON r.AssignedContractorID = u.UserID " +
                                            "LEFT JOIN RequestCustomDays rcd ON r.RequestID = rcd.RequestID "
                            );

                            List<String> conditions = new ArrayList<>();
                            Map<String, Object> bindings = new HashMap<>();

                            List<String> statuses;
                            if (archived) {
                                statuses = List.of("Closed");
                            } else if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                                statuses = List.of(status);
                            } else {
                                statuses = List.of("In work", "Done");
                            }
                            conditions.add("r.Status IN (:statuses)");
                            bindings.put("statuses", statuses);

                            if (overdue != null && overdue) {
                                conditions.add("r.IsOverdue = :isOverdue");
                                bindings.put("isOverdue", true);
                            }
                            if (searchTerm != null && !searchTerm.isBlank()) {
                                conditions.add("UPPER(r.Description) LIKE UPPER(:searchTerm)");
                                bindings.put("searchTerm", "%" + searchTerm + "%");
                            }
                            if (workCategoryId != null) {
                                conditions.add("r.WorkCategoryID = :workCatId");
                                bindings.put("workCatId", workCategoryId);
                            }
                            if (urgencyId != null) {
                                conditions.add("r.UrgencyID = :urgencyId");
                                bindings.put("urgencyId", urgencyId);
                            }

                            Mono<Void> roleConditionsMono = Mono.just(user).flatMap(u -> {
                                String userRole = role.getRoleName();
                                if ("RetailAdmin".equals(userRole)) {
                                    if (shopId != null) {
                                        conditions.add("r.ShopID = :shopId");
                                        bindings.put("shopId", shopId);
                                    }
                                    if (contractorId != null) {
                                        conditions.add("r.AssignedContractorID = :contractorId");
                                        bindings.put("contractorId", contractorId);
                                    }
                                } else if ("Contractor".equals(userRole)) {
                                    conditions.add("r.AssignedContractorID = :userId");
                                    bindings.put("userId", u.getUserID());
                                } else if ("StoreManager".equals(userRole)) {
                                    return shopRepository.findAllByUserID(u.getUserID())
                                            .map(Shop::getShopID)
                                            .collectList()
                                            .doOnNext(shopIds -> {
                                                if (shopIds.isEmpty()) {
                                                    conditions.add("1 = 0");
                                                } else {
                                                    conditions.add("r.ShopID IN (:shopIds)");
                                                    bindings.put("shopIds", shopIds);
                                                }
                                            }).then();
                                }
                                return Mono.empty();
                            });

                            return roleConditionsMono.then(Mono.defer(() -> {
                                if (!conditions.isEmpty()) {
                                    sqlBuilder.append(" WHERE ").append(String.join(" AND ", conditions));
                                }

                                String countSql = "SELECT COUNT(*) FROM (" + sqlBuilder.toString() + ") as count_subquery";
                                DatabaseClient.GenericExecuteSpec countSpec = databaseClient.sql(countSql);
                                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                                    countSpec = countSpec.bind(entry.getKey(), entry.getValue());
                                }
                                Mono<Long> countMono = countSpec.map(row -> row.get(0, Long.class)).one();

                                sqlBuilder.append(parseSortToSql(sort));
                                sqlBuilder.append(" OFFSET ").append((long) page * size).append(" ROWS FETCH NEXT ").append(size).append(" ROWS ONLY");

                                DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sqlBuilder.toString());
                                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                                    spec = spec.bind(entry.getKey(), entry.getValue());
                                }

                                Flux<RequestResponse> resultFlux = spec.map(MAPPING_FUNCTION).all()
                                        .map(this::withCalculatedDaysRemaining);

                                return Mono.zip(resultFlux.collectList(), countMono)
                                        .map(tuple -> {
                                            List<RequestResponse> content = tuple.getT1();
                                            long total = tuple.getT2();
                                            int totalPages = (total == 0) ? 0 : (int) Math.ceil((double) total / size);
                                            return new PagedResponse<>(content, page, total, totalPages);
                                        });

                            }));
                        }));
    }

    private String parseSortToSql(List<String> sortParams) {
        if (sortParams == null || sortParams.isEmpty()) {
            return " ORDER BY r.RequestID DESC";
        }
        final String deadlineExpression = "DATEADD(day, CASE WHEN uc.UrgencyName = 'Customizable' THEN rcd.Days ELSE uc.DefaultDays END, r.CreatedAt)";

        Map<String, String> columnMapping = Map.of(
                "requestID", "r.RequestID",
                "description", "r.Description",
                "shopName", "s.ShopName",
                "workCategoryName", "wc.WorkCategoryName",
                "urgencyName", "uc.UrgencyName",
                "assignedContractorName", "AssignedContractorName",
                "status", "r.Status",
                "daysRemaining", deadlineExpression
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

        return orders.isEmpty() ? " ORDER BY r.RequestID DESC" : " ORDER BY " + orders;
    }


    public Mono<RequestResponse> createAndEnrichRequest(CreateRequestRequest dto, Integer createdByUserId) {
        return createRequest(dto, createdByUserId)
                .flatMap(request -> enrichRequest(request.getRequestID()));
    }

    private Mono<RequestResponse> enrichRequest(Integer requestId) {
        //noinspection SqlResolve
        String sql = "SELECT r.RequestID, r.Description, r.ShopID, r.WorkCategoryID, r.UrgencyID, r.AssignedContractorID, r.Status, r.CreatedAt, r.ClosedAt, r.IsOverdue, " +
                "s.ShopName, wc.WorkCategoryName, uc.UrgencyName, u.Login as AssignedContractorName, " +
                "CASE WHEN uc.UrgencyName = 'Customizable' THEN rcd.Days ELSE uc.DefaultDays END as DaysForTask, " +
                "(SELECT COUNT(*) FROM RequestComments rc WHERE rc.RequestID = r.RequestID) as CommentCount, " +
                "(SELECT COUNT(*) FROM RequestPhotos rp WHERE rp.RequestID = r.RequestID) as PhotoCount " +
                "FROM Requests r " +
                "LEFT JOIN Shops s ON r.ShopID = s.ShopID " +
                "LEFT JOIN WorkCategories wc ON r.WorkCategoryID = wc.WorkCategoryID " +
                "LEFT JOIN UrgencyCategories uc ON r.UrgencyID = uc.UrgencyID " +
                "LEFT JOIN Users u ON r.AssignedContractorID = u.UserID " +
                "LEFT JOIN RequestCustomDays rcd ON r.RequestID = rcd.RequestID " +
                "WHERE r.RequestID = :requestId";

        return databaseClient.sql(sql)
                .bind("requestId", requestId)
                .map(MAPPING_FUNCTION)
                .one()
                .map(this::withCalculatedDaysRemaining);
    }

    private RequestResponse withCalculatedDaysRemaining(RequestResponse response) {
        Integer daysRemaining = null;
        if (response.daysForTask() != null && !"Closed".equals(response.status())) {
            LocalDateTime deadline = response.createdAt().plusDays(response.daysForTask());
            daysRemaining = (int) Duration.between(LocalDateTime.now(), deadline).toDays();
        }
        return new RequestResponse(
                response.requestID(), response.description(), response.shopName(), response.shopID(),
                response.workCategoryName(), response.workCategoryID(), response.urgencyName(), response.urgencyID(),
                response.assignedContractorName(), response.assignedContractorID(), response.status(),
                response.createdAt(), response.closedAt(), daysRemaining, response.daysForTask(),
                response.isOverdue(), response.commentCount(), response.photoCount()
        );
    }

    protected Mono<Request> createRequest(CreateRequestRequest dto, Integer createdByUserId) {
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

        return requestRepository.save(request)
                .flatMap(savedRequest -> {
                    if (dto.customDays() != null) {
                        return template.selectOne(Query.query(Criteria.where("UrgencyID").is(dto.urgencyID())), UrgencyCategory.class)
                                .flatMap(urgency -> {
                                    if ("Customizable".equalsIgnoreCase(urgency.getUrgencyName())) {
                                        RequestCustomDay customDay = new RequestCustomDay();
                                        customDay.setRequestID(savedRequest.getRequestID());
                                        customDay.setDays(dto.customDays());
                                        return customDayRepository.save(customDay).thenReturn(savedRequest);
                                    }
                                    return Mono.just(savedRequest);
                                });
                    }
                    return Mono.just(savedRequest);
                });
    }

    public Mono<RequestResponse> updateAndEnrichRequest(Integer requestId, UpdateRequestRequest dto) {
        return updateRequest(requestId, dto)
                .flatMap(request -> enrichRequest(request.getRequestID()));
    }

    protected Mono<Request> updateRequest(Integer requestId, UpdateRequestRequest dto) {
        return requestRepository.findById(requestId)
                .zipWith(template.selectOne(Query.query(Criteria.where("UrgencyID").is(dto.urgencyID())), UrgencyCategory.class))
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
                                    existingCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(existingCustomDay).then();
                                } else {
                                    return customDayRepository.delete(existingCustomDay);
                                }
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                if (isCustomizable && dto.customDays() != null) {
                                    RequestCustomDay newCustomDay = new RequestCustomDay();
                                    newCustomDay.setRequestID(requestId);
                                    newCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(newCustomDay).then();
                                }
                                return Mono.empty();
                            }));

                    return customDaysLogic.then(updatedRequestMono);
                });
    }


    public Mono<Void> deleteRequest(Integer requestId) {
        return requestRepository.deleteById(requestId);
    }

    public Flux<Integer> getPhotoIdsForRequest(Integer requestId) {
        return photoRepository.findByRequestID(requestId)
                .map(RequestPhoto::getRequestPhotoID);
    }

    public Mono<byte[]> getPhotoById(Integer photoId) {
        return photoRepository.findById(photoId)
                .map(RequestPhoto::getImageData);
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
                .flatMap(savedRequest -> enrichRequest(savedRequest.getRequestID()));
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
                .flatMap(savedRequest -> enrichRequest(savedRequest.getRequestID()));
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