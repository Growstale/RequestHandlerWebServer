package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.RequestComment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;

@Repository
public interface ReactiveRequestCommentRepository extends ReactiveCrudRepository<RequestComment, Integer> {
    Flux<RequestComment> findByRequestIDOrderByCreatedAtAsc(Integer requestID);
    Mono<Void> deleteByParentCommentID(Integer parentCommentID);
}