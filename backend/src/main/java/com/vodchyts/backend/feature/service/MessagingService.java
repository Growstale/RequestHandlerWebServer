package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.OperationNotAllowedException;
import com.vodchyts.backend.feature.dto.CreateMessageTemplateRequest;
import com.vodchyts.backend.feature.dto.MessageTemplateResponse;
import com.vodchyts.backend.feature.dto.SendMessageRequest;
import com.vodchyts.backend.feature.entity.MessageRecipient;
import com.vodchyts.backend.feature.entity.MessageTemplate;
import com.vodchyts.backend.feature.repository.ReactiveMessageRecipientRepository;
import com.vodchyts.backend.feature.repository.ReactiveMessageTemplateRepository;
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

    public MessagingService(ReactiveMessageTemplateRepository templateRepository,
                            ReactiveMessageRecipientRepository recipientRepository,
                            TransactionalOperator transactionalOperator) {
        this.templateRepository = templateRepository;
        this.recipientRepository = recipientRepository;
        this.transactionalOperator = transactionalOperator;
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
                                                : recipients.stream()
                                                .map(MessageRecipient::getShopContractorChatID)
                                                .toList();
                                        return mapToResponse(template, chatIds);
                                    })
                                    .collect(Collectors.toList())
                            )
                            .flatMapMany(Flux::fromIterable);
                });
    }

    public Mono<MessageTemplateResponse> createTemplate(CreateMessageTemplateRequest request) {
        Mono<MessageTemplate> transaction = templateRepository.findByTitle(request.title())
                .flatMap(existing -> Mono.<MessageTemplate>error(new OperationNotAllowedException("Шаблон с таким названием уже существует.")))
                .switchIfEmpty(Mono.defer(() -> {
                    MessageTemplate template = new MessageTemplate();
                    template.setTitle(request.title());
                    template.setMessage(request.message());
                    template.setCreatedAt(LocalDateTime.now());
                    return templateRepository.save(template);
                }))
                .flatMap(savedTemplate -> {
                    if (request.recipientChatIds() != null && !request.recipientChatIds().isEmpty()) {
                        List<MessageRecipient> recipients = request.recipientChatIds().stream()
                                .map(chatId -> {
                                    MessageRecipient recipient = new MessageRecipient();
                                    recipient.setMessageID(savedTemplate.getMessageID());
                                    recipient.setShopContractorChatID(chatId);
                                    return recipient;
                                }).toList();
                        return recipientRepository.saveAll(recipients)
                                .then(Mono.just(savedTemplate));
                    }
                    return Mono.just(savedTemplate);
                });

        return transaction
                .map(finalTemplate -> mapToResponse(finalTemplate, request.recipientChatIds() != null ? request.recipientChatIds() : List.of()))
                .as(transactionalOperator::transactional);
    }

    public Mono<MessageTemplateResponse> updateTemplate(Integer templateId, CreateMessageTemplateRequest request) {
        Mono<MessageTemplate> transaction = templateRepository.findByTitle(request.title())
                .flatMap(existing -> {
                    if (!existing.getMessageID().equals(templateId)) {
                        return Mono.error(new OperationNotAllowedException("Шаблон с таким названием уже существует."));
                    }
                    return Mono.empty();
                })
                .then(templateRepository.findById(templateId))
                .switchIfEmpty(Mono.error(new RuntimeException("Шаблон не найден")))
                .flatMap(template -> {
                    template.setTitle(request.title());
                    template.setMessage(request.message());
                    return templateRepository.save(template);
                })
                .flatMap(savedTemplate ->
                        recipientRepository.deleteByMessageID(savedTemplate.getMessageID())
                                .then(Mono.defer(() -> {
                                    if (request.recipientChatIds() != null && !request.recipientChatIds().isEmpty()) {
                                        List<MessageRecipient> recipients = request.recipientChatIds().stream()
                                                .map(chatId -> {
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

    public Mono<Void> sendMessage(SendMessageRequest request) {
        System.out.println("--- НАЧАЛО ОТПРАВКИ СООБЩЕНИЯ ---");
        System.out.println("Текст сообщения: " + request.message());
        System.out.println("ID чатов получателей: " + request.recipientChatIds());
        System.out.println("--- КОНЕЦ ОТПРАВКИ СООБЩЕНИЯ ---");
        return Mono.empty();
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
}