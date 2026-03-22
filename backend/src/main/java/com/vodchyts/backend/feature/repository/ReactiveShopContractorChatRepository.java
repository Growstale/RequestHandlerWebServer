package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.ShopContractorChat;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveShopContractorChatRepository extends ReactiveCrudRepository<ShopContractorChat, Integer> {

    Mono<Boolean> existsByShopIDAndContractorID(Integer shopId, Integer contractorId);
    Mono<Boolean> existsByShopIDAndContractorIDAndShopContractorChatIDNot(Integer shopId, Integer contractorId, Integer currentId);

    Mono<Boolean> existsByShopIDIsNullAndContractorID(Integer contractorId);
    Mono<Boolean> existsByShopIDIsNullAndContractorIDAndShopContractorChatIDNot(Integer contractorId, Integer currentId);

    Mono<Boolean> existsByShopIDAndContractorIDIsNull(Integer shopId);
    Mono<Boolean> existsByShopIDAndContractorIDIsNullAndShopContractorChatIDNot(Integer shopId, Integer currentId);

    @Query("""
        SELECT TOP 1 scc.TelegramID 
        FROM ShopContractorChats scc
        JOIN Requests r ON r.RequestID = :requestId
        WHERE 
            (scc.ShopID = r.ShopID AND scc.ContractorID = r.AssignedContractorID)
            OR
            (scc.ShopID IS NULL AND scc.ContractorID = r.AssignedContractorID)
            OR
            (scc.ShopID = r.ShopID AND scc.ContractorID IS NULL)
        ORDER BY 
            CASE 
                WHEN scc.ShopID IS NOT NULL AND scc.ContractorID IS NOT NULL THEN 3
                WHEN scc.ShopID IS NULL AND scc.ContractorID IS NOT NULL THEN 2
                WHEN scc.ShopID IS NOT NULL AND scc.ContractorID IS NULL THEN 1
                ELSE 0
            END DESC
    """)
    Mono<Long> findTelegramIdByRequestId(Integer requestId);

    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END 
        FROM ShopContractorChats 
        WHERE (ShopID = :shopId AND ContractorID = :contractorId)
           OR (ShopID IS NULL AND ContractorID = :contractorId)
           OR (ShopID = :shopId AND ContractorID IS NULL)
    """)
    Mono<Boolean> checkCoverage(Integer shopId, Integer contractorId);
}