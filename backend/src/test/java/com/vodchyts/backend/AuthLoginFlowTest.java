package com.vodchyts.backend;

import com.vodchyts.backend.feature.dto.LoginRequest;
import com.vodchyts.backend.feature.dto.LoginResponse;
import com.vodchyts.backend.feature.dto.UserInfoResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = BackendApplication.class
)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)

public class AuthLoginFlowTest {

    @Autowired
    private WebTestClient webTestClient;

    private static String accessToken;
    private static String refreshTokenCookie;

    private final String login = "VodchytsVitali";
    private final String password = "password123";

    @Test
    @Order(1)
    @DisplayName("Вход в систему с существующим пользователем")
    void loginUser() {
        LoginRequest request = new LoginRequest(login, password);

        var response = webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), LoginRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult();

        accessToken = response.getResponseBody().accessToken();
        assertThat(accessToken).isNotBlank();

        refreshTokenCookie = Objects.requireNonNull(response.getResponseCookies().getFirst("refreshToken")).getValue();
        assertThat(refreshTokenCookie).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Доступ к /whoami с валидным access токеном")
    void whoAmI() {
        var response = webTestClient.get()
                .uri("/api/user/whoami")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInfoResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.login()).isEqualTo(login);
        assertThat(response.role()).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("Обновление access токена через refresh токен")
    void refreshAccessToken() {
        var response = webTestClient.post()
                .uri("/api/auth/refresh")
                .cookie("refreshToken", refreshTokenCookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isNotBlank();

        accessToken = response.accessToken();
    }

    @Test
    @Order(4)
    @DisplayName("Выход (logout) и удаление refresh токена")
    void logoutUser() {
        webTestClient.post()
                .uri("/api/auth/logout")
                .cookie("refreshToken", refreshTokenCookie)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/auth/refresh")
                .cookie("refreshToken", refreshTokenCookie)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
