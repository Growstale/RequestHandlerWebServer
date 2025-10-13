package com.vodchyts.backend.feature.controller;

import com.vodchyts.backend.feature.dto.RoleResponse;
import com.vodchyts.backend.feature.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public Flux<RoleResponse> getAllRoles() {
        return roleService.getAllRoles()
                .map(role -> new RoleResponse(role.getRoleID(), role.getRoleName()));
    }
}