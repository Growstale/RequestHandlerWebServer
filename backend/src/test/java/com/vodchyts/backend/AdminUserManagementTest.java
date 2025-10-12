package com.vodchyts.backend;

import com.vodchyts.backend.feature.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = BackendApplication.class
)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Интеграционные тесты для управления пользователями (Admin Flow)")
public class AdminUserManagementTest {

    @Autowired
    private WebTestClient webTestClient;

    // --- Учетные данные для тестов ---
    private static final String ADMIN_LOGIN = "VodchytsVitali"; // ЗАМЕНИТЕ НА ВАШЕГО АДМИНА
    private static final String ADMIN_PASSWORD = "password123"; // ЗАМЕНИТЕ НА ПАРОЛЬ АДМИНА

    private static final String REGULAR_USER_LOGIN = "user"; // ЗАМЕНИТЕ НА ОБЫЧНОГО ПОЛЬЗОВАТЕЛЯ
    private static final String REGULAR_USER_PASSWORD = "UserPass123!"; // ЗАМЕНИТЕ НА ЕГО ПАРОЛЬ

    // --- Переменные для хранения состояния между тестами ---
    private static String adminAccessToken;
    private static String regularUserAccessToken;
    private static Integer createdUserId;
    private static final String newUserLogin = "testuser-" + UUID.randomUUID();
    private static final String newUserPassword = "TestPassword123$";


    @Test
    @Order(1)
    @DisplayName("1. Вход в систему как администратор")
    void loginAsAdmin() {
        LoginRequest request = new LoginRequest(ADMIN_LOGIN, ADMIN_PASSWORD);

        adminAccessToken = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), LoginRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();

        assertThat(adminAccessToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("2. Вход в систему как обычный пользователь")
    void loginAsRegularUser() {
        LoginRequest request = new LoginRequest(REGULAR_USER_LOGIN, REGULAR_USER_PASSWORD);

        regularUserAccessToken = webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), LoginRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();

        assertThat(regularUserAccessToken).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("3. Администратор создает нового пользователя (успешно)")
    void adminCreatesUser_Success() {
        CreateUserRequest request = new CreateUserRequest(
                newUserLogin,
                newUserPassword,
                "Contractor",
                "info@test.com",
                "111222333"
        );

        UserResponse response = webTestClient.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), CreateUserRequest.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.login()).isEqualTo(newUserLogin);
        assertThat(response.roleName()).isEqualTo("Contractor");
        assertThat(response.contactInfo()).isEqualTo("info@test.com");
        assertThat(response.userID()).isNotNull();

        // Сохраняем ID для последующих тестов
        createdUserId = response.userID();
    }

    @Test
    @Order(4)
    @DisplayName("4. Администратор пытается создать пользователя с существующим логином (конфликт)")
    void adminCreatesUser_Conflict() {
        CreateUserRequest request = new CreateUserRequest(newUserLogin, newUserPassword, "Contractor", null, null);

        webTestClient.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), CreateUserRequest.class)
                .exchange()
                .expectStatus().isEqualTo(409); // Conflict
    }

    @Test
    @Order(5)
    @DisplayName("5. Администратор получает список всех пользователей")
    void adminGetsAllUsers() {
        webTestClient.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserResponse.class)
                .value(userList -> {
                    assertThat(userList).isNotEmpty();
                    assertThat(userList.stream().anyMatch(user -> user.userID().equals(createdUserId))).isTrue();
                });
    }

    @Test
    @Order(6)
    @DisplayName("6. Администратор получает отфильтрованный список пользователей по роли")
    void adminGetsUsersByRole() {
        webTestClient.get().uri("/api/admin/users?role=Contractor")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserResponse.class)
                .value(userList -> {
                    assertThat(userList).allMatch(user -> user.roleName().equals("Contractor"));
                    assertThat(userList.stream().anyMatch(user -> user.userID().equals(createdUserId))).isTrue();
                });
    }

    @Test
    @Order(7)
    @DisplayName("7. Администратор получает отсортированный список пользователей")
    void adminGetsUsers_WithSorting() {
        // --- Setup: Создаем дополнительных пользователей для надежного теста сортировки ---
        CreateUserRequest userA_Request = new CreateUserRequest("a-sort-user", newUserPassword, "StoreManager", null, null);
        CreateUserRequest userZ_Request = new CreateUserRequest("z-sort-user", newUserPassword, "Contractor", null, null);

        UserResponse userA = webTestClient.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .bodyValue(userA_Request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        assertThat(userA).isNotNull();

        UserResponse userZ = webTestClient.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .bodyValue(userZ_Request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        assertThat(userZ).isNotNull();

        // --- Тест 1: Сортировка по логину (по возрастанию) ---
        webTestClient.get().uri("/api/admin/users?sort=login,asc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserResponse.class)
                .value(userList -> {
                    assertThat(userList).isSortedAccordingTo(Comparator.comparing(UserResponse::login, String.CASE_INSENSITIVE_ORDER));
                });

        // --- Тест 2: Сортировка по роли (по убыванию), затем по логину (по возрастанию) ---
        webTestClient.get().uri("/api/admin/users?sort=roleName,desc&sort=login,asc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserResponse.class)
                .value(userList -> {
                    Comparator<UserResponse> comparator = Comparator.comparing(UserResponse::roleName, Comparator.reverseOrder())
                            .thenComparing(UserResponse::login, String.CASE_INSENSITIVE_ORDER);
                    assertThat(userList).isSortedAccordingTo(comparator);
                });

        // --- Очистка: Удаляем созданных пользователей ---
        webTestClient.delete().uri("/api/admin/users/" + userA.userID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.delete().uri("/api/admin/users/" + userZ.userID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @Order(8)
    @DisplayName("8. Администратор обновляет данные пользователя")
    void adminUpdatesUser() {
        UpdateUserRequest request = new UpdateUserRequest(
                null, // Пароль не меняем
                "StoreManager",
                "updated.info@test.com",
                "999888777"
        );

        UserResponse response = webTestClient.put().uri("/api/admin/users/" + createdUserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), UpdateUserRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.roleName()).isEqualTo("StoreManager");
        assertThat(response.contactInfo()).isEqualTo("updated.info@test.com");
        assertThat(response.telegramID()).isEqualTo(999888777L);
    }

    @Test
    @Order(9)
    @DisplayName("9. Обычный пользователь пытается получить список пользователей (Forbidden)")
    void regularUserTriesToGetUsers_Forbidden() {
        webTestClient.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + regularUserAccessToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @Order(10)
    @DisplayName("10. Попытка доступа без токена (Unauthorized)")
    void accessWithoutToken_Unauthorized() {
        webTestClient.get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Order(11)
    @DisplayName("11. Администратор удаляет пользователя")
    void adminDeletesUser() {
        webTestClient.delete().uri("/api/admin/users/" + createdUserId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @Order(12)
    @DisplayName("12. Проверка, что пользователь был удален")
    void verifyUserDeletion() {
        webTestClient.get().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserResponse.class)
                .value(userList -> {
                    assertThat(userList.stream().noneMatch(user -> user.userID().equals(createdUserId))).isTrue();
                });
    }
}