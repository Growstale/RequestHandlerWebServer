package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.entity.Role;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class RoleService {

    private final ReactiveRoleRepository roleRepository;

    public RoleService(ReactiveRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public Flux<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}