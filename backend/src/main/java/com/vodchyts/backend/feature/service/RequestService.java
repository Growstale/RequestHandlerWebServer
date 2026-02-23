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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class RequestService {

    private static final long MAX_SINGLE_FILE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_MIMES = List.of("image/jpeg", "image/png");

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;
    private final ReactiveRequestRepository requestRepository;
    private final ReactiveRequestCustomDayRepository customDayRepository;
    private final ReactiveRequestCommentRepository commentRepository;
    private final ReactiveRequestPhotoRepository photoRepository;
    private final ReactiveRoleRepository roleRepository;
    private final ReactiveUserRepository userRepository;
    private final ReactiveShopRepository shopRepository;
    private final TelegramNotificationService notificationService;
    private final ReactiveShopContractorChatRepository chatRepository;
    private final ReactiveWorkCategoryRepository workCategoryRepository;
    private final ReactiveUrgencyCategoryRepository urgencyCategoryRepository;
    private final WebNotificationService webNotificationService;
    private final UpdateBroadcaster updateBroadcaster;

    public RequestService(R2dbcEntityTemplate template, DatabaseClient databaseClient, ReactiveRequestRepository requestRepository, ReactiveRequestCustomDayRepository customDayRepository, ReactiveRequestCommentRepository commentRepository, ReactiveRequestPhotoRepository photoRepository, ReactiveRoleRepository roleRepository, ReactiveUserRepository userRepository, ReactiveShopRepository shopRepository, TelegramNotificationService notificationService, ReactiveShopContractorChatRepository chatRepository, ReactiveWorkCategoryRepository workCategoryRepository, ReactiveUrgencyCategoryRepository urgencyCategoryRepository, WebNotificationService webNotificationService, UpdateBroadcaster updateBroadcaster) {
        this.template = template;
        this.databaseClient = databaseClient;
        this.requestRepository = requestRepository;
        this.customDayRepository = customDayRepository;
        this.commentRepository = commentRepository;
        this.photoRepository = photoRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.notificationService = notificationService;
        this.chatRepository = chatRepository;
        this.workCategoryRepository = workCategoryRepository;
        this.urgencyCategoryRepository = urgencyCategoryRepository;
        this.webNotificationService = webNotificationService;
        this.updateBroadcaster = updateBroadcaster;
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
            Integer urgencyId, Integer contractorId, String status, Boolean overdue,
            LocalDate startDate, LocalDate endDate,
            List<String> sort, int page, int size,
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

                            if (startDate != null) {
                                conditions.add("CAST(r.CreatedAt AS DATE) >= :startDate");
                                bindings.put("startDate", startDate);
                            }
                            if (endDate != null) {
                                String deadlineCalculation = "DATEADD(day, CASE WHEN uc.UrgencyName = 'Customizable' THEN rcd.Days ELSE uc.DefaultDays END, r.CreatedAt)";

                                conditions.add("CAST(" + deadlineCalculation + " AS DATE) <= :endDate");
                                bindings.put("endDate", endDate);
                            }

                            Mono<Void> roleConditionsMono = Mono.just(user).flatMap(u -> {
                                String userRole = role.getRoleName();
                                if (shopId != null) {
                                    conditions.add("r.ShopID = :shopId");
                                    bindings.put("shopId", shopId);
                                }
                                if ("RetailAdmin".equals(userRole)) {
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

    private String getStatusDisplayName(String status) {
        if (status == null) return "—";
        return switch (status) {
            case "In work" -> "В работе";
            case "Done" -> "Выполнена";
            case "Closed" -> "Закрыта";
            default -> status;
        };
    }

    private String getUrgencyDisplayName(String urgencyName) {
        if (urgencyName == null) return "—";
        return switch (urgencyName) {
            case "Emergency" -> "Аварийная";
            case "Urgent" -> "Срочная";
            case "Planned" -> "Плановая";
            case "Customizable" -> "Настраиваемая";
            default -> urgencyName;
        };
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
                .flatMap(request -> enrichRequest(request.getRequestID()))
                .flatMap(this::sendCreationNotification);
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

        if (!"Notes".equalsIgnoreCase(response.urgencyName()) &&
                response.daysForTask() != null &&
                !"Closed".equals(response.status())) {

            LocalDateTime deadline = response.createdAt().plusDays(response.daysForTask());
            daysRemaining = (int) Duration.between(LocalDateTime.now(), deadline).toDays();
        }

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
                })
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
    }

    public Mono<RequestResponse> updateAndEnrichRequest(Integer requestId, UpdateRequestRequest dto) {
        return updateRequest(requestId, dto)
                .flatMap(tuple -> {
                    Request savedReq = tuple.getT1();
                    List<String> changes = tuple.getT2();

                    if (changes.isEmpty()) {
                        return Mono.just(savedReq);
                    }

                    String msg = "✏️ *ЗАЯВКА \\#" + requestId + " ОБНОВЛЕНА*\n\n" + String.join("\n", changes);

                    // Отправка в ТГ
                    Mono<Void> tg = chatRepository.findTelegramIdByRequestId(requestId)
                            .flatMap(chatId -> notificationService.sendNotification(chatId, msg))
                            .onErrorResume(e -> Mono.empty()).then();

                    // Добавляем отправку в ВЕБ
                    Mono<Void> web = webNotificationService.send(
                            requestId,
                            "Обновление заявки #" + requestId,
                            "Администратор изменил параметры заявки.",
                            savedReq.getAssignedContractorID()
                    );

                    return Mono.when(tg, web).thenReturn(savedReq);
                })
                .flatMap(request -> enrichRequest(request.getRequestID()));
    }

    protected Mono<Tuple2<Request, List<String>>> updateRequest(Integer requestId, UpdateRequestRequest dto) {
        Mono<Request> requestMono = requestRepository.findById(requestId);

        Mono<UrgencyCategory> urgencyMono = urgencyCategoryRepository.findById(dto.urgencyID())
                .switchIfEmpty(Mono.error(new RuntimeException("Срочность не найдена")));

        Mono<Integer> oldCustomDaysMono = customDayRepository.findByRequestID(requestId)
                .map(RequestCustomDay::getDays)
                .defaultIfEmpty(0);

        Mono<String> shopNameMono = shopRepository.findById(dto.shopID())
                .map(Shop::getShopName).defaultIfEmpty("Неизвестный магазин");
        Mono<String> workNameMono = workCategoryRepository.findById(dto.workCategoryID())
                .map(WorkCategory::getWorkCategoryName).defaultIfEmpty("Неизвестный вид работ");
        Mono<String> contractorNameMono = dto.assignedContractorID() != null
                ? userRepository.findById(dto.assignedContractorID()).map(User::getLogin).defaultIfEmpty("Не назначен")
                : Mono.just("Не назначен");

        return Mono.zip(requestMono, urgencyMono, shopNameMono, workNameMono, contractorNameMono, oldCustomDaysMono)
                .flatMap(tuple -> {
                    Request request = tuple.getT1();
                    UrgencyCategory newUrgency = tuple.getT2();
                    String newShopName = tuple.getT3();
                    String newWorkName = tuple.getT4();
                    String newContractorName = tuple.getT5();
                    Integer oldCustomDays = tuple.getT6();

                    List<String> changes = new ArrayList<>();

                    if (!Objects.equals(request.getStatus(), dto.status())) {
                        changes.add(String.format("📊 *Статус:* %s ➡️ %s",
                                getStatusDisplayName(request.getStatus()),
                                getStatusDisplayName(dto.status())));
                    }

                    if (!Objects.equals(request.getAssignedContractorID(), dto.assignedContractorID())) {
                        changes.add("👷 *Исполнитель:* " + notificationService.escapeMarkdown(newContractorName));
                    }

                    if (!Objects.equals(request.getShopID(), dto.shopID())) {
                        changes.add("🏪 *Магазин:* " + notificationService.escapeMarkdown(newShopName));
                    }

                    if (!Objects.equals(request.getWorkCategoryID(), dto.workCategoryID())) {
                        changes.add("🛠 *Вид работ:* " + notificationService.escapeMarkdown(newWorkName));
                    }

                    boolean isCustomizable = "Customizable".equalsIgnoreCase(newUrgency.getUrgencyName());
                    boolean urgencyIdChanged = !Objects.equals(request.getUrgencyID(), dto.urgencyID());
                    boolean daysChanged = isCustomizable && !Objects.equals(oldCustomDays, dto.customDays());

                    if (urgencyIdChanged || daysChanged) {
                        String localizedUrgency = getUrgencyDisplayName(newUrgency.getUrgencyName());

                        if (isCustomizable && dto.customDays() != null) {
                            localizedUrgency += " \\(" + dto.customDays() + " дн\\.\\)";
                        }

                        changes.add("🔥 *Срочность:* " + localizedUrgency);
                    }

                    if (!Objects.equals(request.getDescription(), dto.description())) {
                        String rawDesc = dto.description() != null ? dto.description() : "";

                        String shortDesc = rawDesc.length() > 100
                                ? rawDesc.substring(0, 100) + "..."
                                : rawDesc;

                        String safeDesc = notificationService.escapeMarkdown(shortDesc);

                        changes.add("📝 *Описание:* " + safeDesc);
                    }

                    request.setDescription(dto.description());
                    request.setShopID(dto.shopID());
                    request.setWorkCategoryID(dto.workCategoryID());
                    request.setUrgencyID(dto.urgencyID());
                    request.setAssignedContractorID(dto.assignedContractorID());

                    if (!Objects.equals(request.getStatus(), dto.status()) && "Closed".equalsIgnoreCase(dto.status())) {
                        request.setClosedAt(LocalDateTime.now());
                    }
                    request.setStatus(dto.status());

                    boolean wasOverdue = Boolean.TRUE.equals(request.getIsOverdue());
                    Integer daysForTask = isCustomizable ? dto.customDays() : newUrgency.getDefaultDays();

                    if (daysForTask != null) {
                        LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                        boolean isNowOverdue = LocalDateTime.now().isAfter(deadline);

                        request.setIsOverdue(isNowOverdue);

                        if ("In work".equalsIgnoreCase(request.getStatus())) {
                            if (!wasOverdue && isNowOverdue) {
                                changes.add("❗️ *Внимание:* Срок выполнения истек\\!");
                            } else if (wasOverdue && !isNowOverdue) {
                                changes.add("✅ *Срок:* Просрочка устранена \\(время добавлено\\)");
                            }
                        }
                    } else {
                        request.setIsOverdue(false);
                    }

                    Mono<Request> updatedRequestMono = requestRepository.save(request);

                    Mono<Void> customDaysLogic = customDayRepository.deleteByRequestID(requestId)
                            .then(Mono.defer(() -> {
                                if (isCustomizable && dto.customDays() != null) {
                                    RequestCustomDay newCustomDay = new RequestCustomDay();
                                    newCustomDay.setRequestID(requestId);
                                    newCustomDay.setDays(dto.customDays());
                                    return customDayRepository.save(newCustomDay).then();
                                }
                                return Mono.empty();
                            }));

                    return customDaysLogic.then(updatedRequestMono)
                            .map(savedReq -> Tuples.of(savedReq, changes))
                            .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
                });
    }

    public Mono<Void> deleteRequest(Integer requestId) {
        return requestRepository.deleteById(requestId)
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
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
                                comment.getCreatedAt(),
                                comment.getParentCommentID(),
                                new ArrayList<>()
                        ))
                )
                .collectList()
                .flatMapMany(allComments -> {
                    Map<Integer, CommentResponse> rootComments = new LinkedHashMap<>();
                    List<CommentResponse> replies = new ArrayList<>();

                    for (var c : allComments) {
                        if (c.parentCommentID() == null) {
                            rootComments.put(c.commentID(), c);
                        } else {
                            replies.add(c);
                        }
                    }

                    for (var r : replies) {
                        CommentResponse parent = rootComments.get(r.parentCommentID());
                        if (parent != null) {
                            parent.replies().add(r);
                        }
                    }

                    return Flux.fromIterable(rootComments.values());
                });
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
                            return Mono.error(new OperationNotAllowedException("У вас нет прав для комментирования."));
                        }
                        if ("Closed".equalsIgnoreCase(request.getStatus())) {
                            return Mono.error(new OperationNotAllowedException("Заявка закрыта."));
                        }

                        RequestComment newComment = new RequestComment();
                        newComment.setRequestID(requestId);
                        newComment.setUserID(userId);
                        newComment.setCommentText(dto.commentText());
                        newComment.setCreatedAt(LocalDateTime.now());
                        newComment.setParentCommentID(dto.parentCommentID());

                        return commentRepository.save(newComment)
                                .flatMap(savedComment -> {
                                    String author = notificationService.escapeMarkdown(user.getLogin());
                                    String safeText = notificationService.escapeMarkdown(dto.commentText());
                                    Mono<Void> tgMono;

                                    if (dto.parentCommentID() != null) {
                                        return commentRepository.findById(dto.parentCommentID())
                                                .flatMap(parentComment -> {
                                                    String parentText = parentComment.getCommentText();
                                                    String parentSnippet = parentText.length() > 50
                                                            ? parentText.substring(0, 47) + "..."
                                                            : parentText;

                                                    String safeParentSnippet = notificationService.escapeMarkdown(parentSnippet);

                                                    String msg = String.format(
                                                            "↩️ *ОТВЕТ пользователю в заявке \\#%d*\n" +
                                                                    "💬 _На комментарий: \"%s\"_\n" +
                                                                    "👤 *От:* %s\n\n%s",
                                                            requestId, safeParentSnippet, author, safeText
                                                    );

                                                    return chatRepository.findTelegramIdByRequestId(requestId)
                                                            .flatMap(chatId -> notificationService.sendCommentNotification(chatId, msg, requestId, savedComment.getCommentID()))
                                                            .flatMap(unused -> webNotificationService.send(
                                                                    requestId,
                                                                    "Новый комментарий #" + requestId,
                                                                    "От: " + user.getLogin() + ". Текст: " + dto.commentText(),
                                                                    request.getAssignedContractorID()
                                                            ))
                                                            .thenReturn(savedComment);
                                                });
                                    } else {
                                        String msg = String.format("💬 *Новый комментарий к заявке \\#%d*\n👤 *От:* %s\n\n%s", requestId, author, safeText);
                                        tgMono = chatRepository.findTelegramIdByRequestId(requestId)
                                                .flatMap(chatId -> notificationService.sendCommentNotification(chatId, msg, requestId, savedComment.getCommentID()));
                                    }
                                    Mono<Void> webMono = webNotificationService.send(
                                            requestId,
                                            "Новый комментарий #" + requestId,
                                            "Автор: " + user.getLogin() + ". Текст: " + dto.commentText(),
                                            request.getAssignedContractorID()
                                    );

                                    return Mono.when(tgMono.onErrorResume(e -> Mono.empty()), webMono)
                                            .thenReturn(savedComment);
                                });
                    });
                })
                .flatMap(savedComment -> userRepository.findById(userId).map(user -> new CommentResponse(
                        savedComment.getCommentID(),
                        savedComment.getRequestID(),
                        user.getLogin(),
                        savedComment.getCommentText(),
                        savedComment.getCreatedAt(),
                        savedComment.getParentCommentID(),
                        new ArrayList<>()
                )))
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
    }


    public Flux<byte[]> getPhotosForRequest(Integer requestId) {
        return photoRepository.findByRequestID(requestId)
                .map(RequestPhoto::getImageData);
    }

    public Mono<Void> addPhotosToRequest(Integer requestId, Flux<FilePart> filePartFlux, Integer userId) {
        return requestRepository.findById(requestId)
                .switchIfEmpty(Mono.error(new RuntimeException("Заявка с ID " + requestId + " не найдена")))
                .zipWith(userRepository.findById(userId))
                .flatMap(tuple -> {
                    Request request = tuple.getT1();
                    User user = tuple.getT2();

                    return canUserModify(request, user).flatMap(canModify -> {
                        if (!canModify) return Mono.error(new OperationNotAllowedException("Нет прав для редактирования этой заявки"));
                        if ("Closed".equalsIgnoreCase(request.getStatus()))
                            return Mono.error(new OperationNotAllowedException("Нельзя добавить фото в закрытую заявку"));

                        // Обрабатываем поток файлов
                        return filePartFlux.flatMap(filePart -> {
                                    String contentType = filePart.headers().getContentType() != null
                                            ? filePart.headers().getContentType().toString()
                                            : "";

                                    if (!ALLOWED_MIMES.contains(contentType)) {
                                        return Mono.error(new OperationNotAllowedException(
                                                "Файл " + filePart.filename() + " имеет недопустимый тип данных"));
                                    }

                                    return DataBufferUtils.join(filePart.content())
                                            .flatMap(dataBuffer -> {
                                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                                dataBuffer.read(bytes);
                                                DataBufferUtils.release(dataBuffer);

                                                RequestPhoto photo = new RequestPhoto();
                                                photo.setRequestID(requestId);
                                                photo.setImageData(bytes);

                                                return photoRepository.save(photo)
                                                        .flatMap(savedPhoto -> {
                                                            // Telegram уведомление оставляем для каждого фото (или можно тоже оптимизировать)
                                                            return chatRepository.findTelegramIdByRequestId(requestId)
                                                                    .flatMap(chatId -> {
                                                                        String author = notificationService.escapeMarkdown(user.getLogin());
                                                                        String caption = String.format(
                                                                                "📷 *Новое фото к заявке \\#%d*\n👤 *Добавил:* %s",
                                                                                requestId, author
                                                                        );
                                                                        return notificationService.sendPhoto(chatId, caption, bytes);
                                                                    })
                                                                    .onErrorResume(e -> Mono.empty())
                                                                    .thenReturn(savedPhoto); // Возвращаем объект фото для подсчета
                                                        });
                                            });
                                })
                                .collectList() // Собираем все успешно загруженные фото в список
                                .flatMap(savedPhotos -> {
                                    if (savedPhotos.isEmpty()) return Mono.empty();

                                    // ОТПРАВЛЯЕМ ОДНО ВЕБ-УВЕДОМЛЕНИЕ НА ВСЮ ПАЧКУ
                                    String message = savedPhotos.size() == 1
                                            ? "Пользователь " + user.getLogin() + " добавил фотографию."
                                            : "Пользователь " + user.getLogin() + " добавил фотографии (" + savedPhotos.size() + " шт.).";

                                    return webNotificationService.send(
                                            requestId,
                                            "Новое фото #" + requestId,
                                            message,
                                            request.getAssignedContractorID()
                                    );
                                });
                    });
                })
                .then()
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
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

                    Mono<UrgencyCategory> urgencyMono = urgencyCategoryRepository.findById(request.getUrgencyID());
                    Mono<RequestCustomDay> customDayMono = customDayRepository.findByRequestID(requestId)
                            .defaultIfEmpty(new RequestCustomDay());

                    return Mono.zip(urgencyMono, customDayMono).flatMap(tuple -> {
                        UrgencyCategory urgency = tuple.getT1();
                        RequestCustomDay customDay = tuple.getT2();

                        Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                ? customDay.getDays()
                                : urgency.getDefaultDays();

                        boolean isOverdue = false;
                        if (daysForTask != null) {
                            LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                            isOverdue = LocalDateTime.now().isAfter(deadline);
                        }

                        request.setIsOverdue(isOverdue);

                        return requestRepository.save(request);
                    });
                })
                .flatMap(savedRequest -> {
                    return webNotificationService.send(
                            savedRequest.getRequestID(),
                            "✅ Заявка выполнена #" + savedRequest.getRequestID(),
                            "Исполнитель отметил заявку как выполненную.",
                            null
                    ).thenReturn(savedRequest);
                })
                .flatMap(savedRequest -> enrichRequest(savedRequest.getRequestID()))
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
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

                    Mono<UrgencyCategory> urgencyMono = urgencyCategoryRepository.findById(request.getUrgencyID());
                    Mono<RequestCustomDay> customDayMono = customDayRepository.findByRequestID(requestId)
                            .defaultIfEmpty(new RequestCustomDay());

                    return Mono.zip(urgencyMono, customDayMono)
                            .flatMap(tuple -> {
                                UrgencyCategory urgency = tuple.getT1();
                                RequestCustomDay customDay = tuple.getT2();

                                Integer daysForTask = "Customizable".equalsIgnoreCase(urgency.getUrgencyName())
                                        ? customDay.getDays()
                                        : urgency.getDefaultDays();

                                boolean isOverdue = false;
                                long daysOverdue = 0;

                                if (daysForTask != null) {
                                    LocalDateTime deadline = request.getCreatedAt().plusDays(daysForTask);
                                    isOverdue = LocalDateTime.now().isAfter(deadline);
                                    if (isOverdue) {
                                        daysOverdue = Duration.between(deadline, LocalDateTime.now()).toDays();
                                        daysOverdue = Math.max(1, daysOverdue);
                                    }
                                }

                                request.setIsOverdue(isOverdue);

                                StringBuilder msgBuilder = new StringBuilder();
                                msgBuilder.append("🔄 *ЗАЯВКА \\#").append(requestId).append(" ВОССТАНОВЛЕНА*\n\n");
                                msgBuilder.append("Статус: *Закрыта* ➡️ *В работе*");

                                if (isOverdue) {
                                    msgBuilder.append("\n\n⚠️ *Обратите внимание:* Заявка просрочена на *")
                                            .append(daysOverdue)
                                            .append(" дн\\.*");
                                }

                                String finalMessage = msgBuilder.toString();

                                return requestRepository.save(request)
                                        .flatMap(savedReq -> {
                                            Mono<Void> tg = chatRepository.findTelegramIdByRequestId(requestId)
                                                    .flatMap(chatId -> notificationService.sendNotification(chatId, finalMessage))
                                                    .onErrorResume(e -> Mono.empty());

                                            Mono<Void> web = webNotificationService.send(
                                                    requestId,
                                                    "🔄 Восстановление заявки #" + requestId,
                                                    "Заявка была возвращена из архива в работу.",
                                                    savedReq.getAssignedContractorID()
                                            );

                                            return Mono.when(tg, web).thenReturn(savedReq);
                                        });
                            });
                })
                .flatMap(savedRequest -> enrichRequest(savedRequest.getRequestID()))
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
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
                )
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
    }

    public Mono<RequestResponse> createAndEnrichRequestFromBot(CreateRequestFromBotRequest dto) {
        CreateRequestRequest baseDto = new CreateRequestRequest(
                dto.description(),
                dto.shopID(),
                dto.workCategoryID(),
                dto.urgencyID(),
                dto.assignedContractorID(),
                dto.customDays()
        );
        return createRequest(baseDto, dto.createdByUserID())
                .flatMap(request -> enrichRequest(request.getRequestID()))
                .flatMap(this::sendCreationNotification);
    }

    public Mono<RequestResponse> getRequestById(Integer requestId) {
        return enrichRequest(requestId);
    }

    public Mono<Void> deleteComment(Integer commentId) {
        return commentRepository.findById(commentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Комментарий не найден")))
                .flatMap(comment ->
                        commentRepository.deleteByParentCommentID(commentId)
                                .then(commentRepository.deleteById(commentId))
                )
                .doOnSuccess(v -> updateBroadcaster.publish("REQUESTS_UPDATED"));
    }

    private Mono<RequestResponse> sendCreationNotification(RequestResponse response) {
        String descriptionShort = response.description() != null && response.description().length() > 100
                ? response.description().substring(0, 100) + "..."
                : response.description();

        String safeDescription = notificationService.escapeMarkdown(descriptionShort);
        String safeShop = notificationService.escapeMarkdown(response.shopName());
        String safeWork = notificationService.escapeMarkdown(response.workCategoryName());
        String safeUrgency = notificationService.escapeMarkdown(response.urgencyName());

        String msg = String.format(
                "🆕 *НОВАЯ ЗАЯВКА \\#%d*\n\n" +
                        "🏪 *Магазин:* %s\n" +
                        "🛠 *Вид работ:* %s\n" +
                        "🔥 *Срочность:* %s\n" +
                        "📝 *Описание:* %s",
                response.requestID(),
                safeShop,
                safeWork,
                safeUrgency,
                safeDescription
        );

        Mono<Void> tgNotification = chatRepository.findTelegramIdByRequestId(response.requestID())
                .flatMap(chatId -> notificationService.sendNotification(chatId, msg))
                .onErrorResume(e -> Mono.empty())
                .then();

        Mono<Void> webNotification = webNotificationService.send(
                response.requestID(),
                "Новая заявка #" + response.requestID(),
                "Магазин: " + response.shopName() + ". " + response.description(),
                response.assignedContractorID()
        );

        return Mono.when(tgNotification, webNotification)
                .thenReturn(response);
    }

    private boolean isValidImageSignature(byte[] data) {
        if (data.length < 4) return false;

        boolean isJpeg = data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF;
        boolean isPng = data[0] == (byte) 0x89 && data[1] == (byte) 0x50 &&
                data[2] == (byte) 0x4E && data[3] == (byte) 0x47;

        return isJpeg || isPng;
    }
}