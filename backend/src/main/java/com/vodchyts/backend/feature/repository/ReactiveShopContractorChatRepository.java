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

    @Query("""
        SELECT TOP 1 scc.TelegramID 
        FROM ShopContractorChats scc
        JOIN Requests r ON r.ShopID = scc.ShopID 
        WHERE r.RequestID = :requestId 
        AND (
            (r.AssignedContractorID IS NOT NULL AND scc.ContractorID = r.AssignedContractorID)
            OR
            (scc.ContractorID IS NULL) -- Fallback на общий чат магазина
        )
        ORDER BY scc.ContractorID DESC -- Приоритет чату с конкретным исполнителем (null в SQL при сортировке обычно идет последним или первым, но DESC для ID > 0 поставит конкретного исполнителя выше NULL)
    """)
    Mono<Long> findTelegramIdByRequestId(Integer requestId);
}