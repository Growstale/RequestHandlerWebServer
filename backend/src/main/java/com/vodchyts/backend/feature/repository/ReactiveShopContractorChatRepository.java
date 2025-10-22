package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.ShopContractorChat;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveShopContractorChatRepository extends ReactiveCrudRepository<ShopContractorChat, Integer> {
    Mono<Boolean> existsByShopIDAndContractorID(Integer shopId, Integer contractorId);
    Mono<Boolean> existsByShopIDAndContractorIDAndShopContractorChatIDNot(Integer shopId, Integer contractorId, Integer currentId);
}