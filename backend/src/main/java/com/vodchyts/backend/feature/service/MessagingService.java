package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.feature.dto.CreateMessageTemplateRequest;
import com.vodchyts.backend.feature.dto.MessageTemplateResponse;
import com.vodchyts.backend.feature.dto.SendMessageRequest;
import com.vodchyts.backend.feature.entity.MessageRecipient;
import com.vodchyts.backend.feature.entity.MessageTemplate;
import com.vodchyts.backend.feature.repository.ReactiveMessageRecipientRepository;
import com.vodchyts.backend.feature.repository.ReactiveMessageTemplateRepository;
import com.vodchyts.backend.feature.repository.ReactiveShopContractorChatRepository;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessagingService {

    private final ReactiveMessageTemplateRepository templateRepository;
    private final ReactiveMessageRecipientRepository recipientRepository;
    private final TransactionalOperator transactionalOperator;
    private final TelegramNotificationService notificationService; // inject
    private final ReactiveShopContractorChatRepository chatRepository; // Нужно для поиска TelegramID

    public MessagingService(ReactiveMessageTemplateRepository templateRepository,
                            ReactiveMessageRecipientRepository recipientRepository,
                            TransactionalOperator transactionalOperator, TelegramNotificationService notificationService, ReactiveShopContractorChatRepository chatRepository) {
        this.templateRepository = templateRepository;
        this.recipientRepository = recipientRepository;
        this.transactionalOperator = transactionalOperator;
        this.notificationService = notificationService;
        this.chatRepository = chatRepository;
    }

    private Mono<byte[]> extractBytes(Mono<FilePart> filePartMono) {
        return filePartMono
                .flatMap(filePart -> DataBufferUtils.join(filePart.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        }))
                .defaultIfEmpty(new byte[0]);
    }

    public Flux<MessageTemplateResponse> getAllTemplates() {
        return templateRepository.findAll()
                .collectList()
                .flatMapMany(templates -> {
                    if (templates.isEmpty()) {
                        return Flux.empty();
                    }
                    List<Integer> templateIds = templates.stream().map(MessageTemplate::getMessageID).toList();
                    return recipientRepository.findByMessageIDIn(templateIds)
                            .collectMultimap(MessageRecipient::getMessageID)
                            .map(recipientsMap -> templates.stream()
                                    .map(template -> {
                                        Collection<MessageRecipient> recipients = recipientsMap.get(template.getMessageID());
                                        List<Integer> chatIds = (recipients == null)
                                                ? List.of()
                                                : recipients.stream().map(MessageRecipient::getShopContractorChatID).toList();
                                        return mapToResponse(template, chatIds);
                                    })
                                    .collect(Collectors.toList()))
                            .flatMapMany(Flux::fromIterable);
                });
    }

    public Mono<MessageTemplateResponse> createTemplate(CreateMessageTemplateRequest request, Mono<FilePart> imageFile) {
        Mono<MessageTemplate> transaction = templateRepository.findByTitle(request.title())
                .flatMap(existing -> Mono.<MessageTemplate>error(new OperationNotAllowedException("Шаблон с таким названием уже существует.")))
                .then(extractBytes(imageFile))
                .flatMap(imageData -> {
                    MessageTemplate template = new MessageTemplate();
                    template.setTitle(request.title());
                    template.setMessage(request.message());
                    template.setCreatedAt(LocalDateTime.now());
                    if (imageData.length > 0) {
                        template.setImageData(imageData);
                    }
                    return templateRepository.save(template);
                })
                .flatMap(savedTemplate -> {
                    if (request.recipientChatIds() != null && !request.recipientChatIds().isEmpty()) {
                        List<MessageRecipient> recipients = request.recipientChatIds().stream().map(chatId -> {
                            MessageRecipient recipient = new MessageRecipient();
                            recipient.setMessageID(savedTemplate.getMessageID());
                            recipient.setShopContractorChatID(chatId);
                            return recipient;
                        }).toList();
                        return recipientRepository.saveAll(recipients).then(Mono.just(savedTemplate));
                    }
                    return Mono.just(savedTemplate);
                });

        return transaction
                .map(finalTemplate -> mapToResponse(finalTemplate, request.recipientChatIds() != null ? request.recipientChatIds() : List.of()))
                .as(transactionalOperator::transactional);
    }

    public Mono<MessageTemplateResponse> updateTemplate(Integer templateId, CreateMessageTemplateRequest request, Mono<FilePart> imageFile) {
        Mono<MessageTemplate> foundTemplate = templateRepository.findById(templateId)
                .switchIfEmpty(Mono.error(new RuntimeException("Шаблон не найден")));

        Mono<MessageTemplate> transaction = foundTemplate
                .flatMap(template ->
                        templateRepository.findByTitle(request.title())
                                .flatMap(existing -> {
                                    if (!existing.getMessageID().equals(templateId)) {
                                        return Mono.<MessageTemplate>error(new OperationNotAllowedException("Шаблон с таким названием уже существует."));
                                    }
                                    return Mono.just(template);
                                })
                                .switchIfEmpty(Mono.just(template))
                )
                .zipWith(extractBytes(imageFile))
                .flatMap(tuple -> {
                    MessageTemplate templateToUpdate = tuple.getT1();
                    byte[] newImageData = tuple.getT2();

                    templateToUpdate.setTitle(request.title());
                    templateToUpdate.setMessage(request.message());
                    if (newImageData.length > 0) {
                        templateToUpdate.setImageData(newImageData);
                    }
                    return templateRepository.save(templateToUpdate);
                })
                .flatMap(savedTemplate ->
                        recipientRepository.deleteByMessageID(savedTemplate.getMessageID())
                                .then(Mono.defer(() -> {
                                    if (request.recipientChatIds() != null && !request.recipientChatIds().isEmpty()) {
                                        List<MessageRecipient> recipients = request.recipientChatIds().stream().map(chatId -> {
                                            MessageRecipient recipient = new MessageRecipient();
                                            recipient.setMessageID(savedTemplate.getMessageID());
                                            recipient.setShopContractorChatID(chatId);
                                            return recipient;
                                        }).toList();
                                        return recipientRepository.saveAll(recipients).then();
                                    }
                                    return Mono.empty();
                                }))
                                .thenReturn(savedTemplate)
                );

        return transaction
                .map(finalTemplate -> mapToResponse(finalTemplate, request.recipientChatIds() != null ? request.recipientChatIds() : List.of()))
                .as(transactionalOperator::transactional);
    }

    public Mono<Void> deleteTemplate(Integer templateId) {
        return templateRepository.deleteById(templateId);
    }

    public Mono<byte[]> getTemplateImage(Integer templateId) {
        return templateRepository.findById(templateId)
                .mapNotNull(MessageTemplate::getImageData);
    }

    public Mono<Void> deleteTemplateImage(Integer templateId) {
        return templateRepository.findById(templateId)
                .switchIfEmpty(Mono.error(new RuntimeException("Шаблон не найден")))
                .flatMap(template -> {
                    template.setImageData(null);
                    return templateRepository.save(template);
                })
                .then();
    }

    public Mono<Void> sendMessage(SendMessageRequest request) {
        return Flux.fromIterable(request.recipientChatIds())
                .flatMap(chatId -> chatRepository.findById(chatId)) // Находим Chat сущность по ID связи
                .flatMap(chat -> notificationService.sendNotification(chat.getTelegramID(), request.message()))
                .then();
    }

    private MessageTemplateResponse mapToResponse(MessageTemplate template, List<Integer> recipientChatIds) {
        return new MessageTemplateResponse(
                template.getMessageID(),
                template.getTitle(),
                template.getMessage(),
                template.getCreatedAt(),
                template.getImageData() != null && template.getImageData().length > 0,
                recipientChatIds
        );
    }

    public Mono<Void> sendMessageWithImage(String message, List<Integer> recipientChatIds, Mono<FilePart> imageFile) {
        return extractBytes(imageFile).flatMap(imageData -> {

            if (imageData.length == 0) {
                return Mono.error(new IllegalArgumentException("Файл изображения пуст или не передан"));
            }

            return Flux.fromIterable(recipientChatIds)
                    .flatMap(chatId -> chatRepository.findById(chatId))
                    .flatMap(chat -> {
                        return notificationService.sendPhoto(chat.getTelegramID(), message, imageData);
                    })
                    .then();
        });
    }



}