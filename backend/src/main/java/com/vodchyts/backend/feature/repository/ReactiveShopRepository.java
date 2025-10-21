package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.Shop;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@Repository
public interface ReactiveShopRepository extends ReactiveCrudRepository<Shop, Integer> {
    Mono<Shop> findByShopName(String shopName);
    Flux<Shop> findAllByUserID(Integer userId);
}
