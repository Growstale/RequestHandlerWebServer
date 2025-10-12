package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.dto.LoginResponse;
import com.vodchyts.backend.feature.dto.LoginResponseWithRefresh;
import com.vodchyts.backend.feature.entity.RefreshToken;
import com.vodchyts.backend.exception.InvalidTokenException;
import com.vodchyts.backend.exception.UserNotFoundException;
import com.vodchyts.backend.feature.repository.ReactiveRefreshTokenRepository;
import com.vodchyts.backend.feature.repository.ReactiveRoleRepository;
import com.vodchyts.backend.feature.repository.ReactiveUserRepository;
import com.vodchyts.backend.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private final ReactiveUserRepository userRepository;
    private final ReactiveRoleRepository roleRepository;
    private final ReactiveRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public AuthService(ReactiveUserRepository userRepository,
                       ReactiveRoleRepository roleRepository,
                       ReactiveRefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public Mono<LoginResponseWithRefresh> login(String login, String password) {
        return userRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return Mono.error(new RuntimeException("Invalid password"));
                    }
                    return roleRepository.findById(user.getRoleID())
                            .flatMap(role -> {
                                String accessToken = jwtUtils.generateAccessToken(user.getLogin(), role.getRoleName());
                                String refreshToken = jwtUtils.generateRefreshToken(user.getLogin());

                                String tokenHash = sha256(refreshToken);

                                RefreshToken tokenEntity = new RefreshToken();
                                tokenEntity.setUserID(user.getUserID());
                                tokenEntity.setTokenHash(tokenHash);
                                tokenEntity.setIssuedAt(LocalDateTime.now());
                                tokenEntity.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)));

                                return refreshTokenRepository.save(tokenEntity)
                                        .thenReturn(new LoginResponseWithRefresh(accessToken, refreshToken));
                            });
                });
    }

    public Mono<LoginResponse> refresh(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            return Mono.error(new InvalidTokenException("Invalid JWT refresh token"));
        }

        String tokenHash = sha256(refreshToken);

        return refreshTokenRepository.findByTokenHash(tokenHash)
                .switchIfEmpty(Mono.error(new InvalidTokenException("Invalid or expired refresh token")))
                .flatMap(rt -> {
                    if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new InvalidTokenException("Refresh token expired"));
                    }

                    String username = jwtUtils.getUsernameFromToken(refreshToken);

                    return userRepository.findByLogin(username)
                            .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
                            .flatMap(user -> roleRepository.findById(user.getRoleID())
                                    .map(role -> {
                                        String newAccessToken = jwtUtils.generateAccessToken(username, role.getRoleName());
                                        return new LoginResponse(newAccessToken);
                                    }));
                });
    }

    public Mono<ResponseEntity<Void>> logout(String refreshToken) {
        String tokenHash = sha256(refreshToken);
        return refreshTokenRepository.deleteByTokenHash(tokenHash)
                .thenReturn(ResponseEntity.ok().build());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
