package com.vodchyts.backend.feature.repository;

import com.vodchyts.backend.feature.entity.Role;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ReactiveRoleRepository extends ReactiveCrudRepository<Role, Integer> {
    Mono<Role> findByRoleName(String roleName);
}
