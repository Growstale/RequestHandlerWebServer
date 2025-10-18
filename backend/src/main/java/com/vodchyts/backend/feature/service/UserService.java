package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.exception.InvalidTokenException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.dto.UserInfoResponse;
import com.vodchyts.backend.feature.entity.User;
import com.vodchyts.backend.feature.repository.ReactiveRefreshTokenRepository;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import com.vodchyts.backend.security.JwtUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserService {
    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final JwtUtils jwtUtils;

    public UserService(ReactiveUserRepository userRepository, ReactiveRoleRepository roleRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtils = jwtUtils;
    }

    public Mono<UserInfoResponse> whoAmI(String accessToken) {
        if (!jwtUtils.validateToken(accessToken)) {
            return Mono.error(new InvalidTokenException("Недействительный или истекший токен доступа"));
        }

        String username = jwtUtils.getUsernameFromToken(accessToken);

        return userRepository.findByLogin(username)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Пользователь не найден")))
                .flatMap(user ->
                        roleRepository.findById(user.getRoleID())
                                .map(role -> new UserInfoResponse(user.getLogin(), role.getRoleName()))
                );
    }

    public Mono<User> findByLogin(String login) {
        return userRepository.findByLogin(login);
    }
}
