package com.vodchyts.backend;

import com.vodchyts.backend.feature.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = BackendApplication.class
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Интеграционные тесты для управления магазинами (Admin Flow)")
public class AdminShopManagementTest {

    @Autowired
    private WebTestClient webTestClient;

    // --- Учетные данные для тестов ---
    private static final String ADMIN_LOGIN = "VodchytsVitali"; // ЗАМЕНИТЕ НА ВАШЕГО АДМИНА
    private static final String ADMIN_PASSWORD = "password123"; // ЗАМЕНИТЕ НА ПАРОЛЬ АДМИНА

    // --- Переменные для хранения состояния между тестами ---
    private static String adminAccessToken;

    // Пользователи, необходимые для тестов
    private static Integer storeManagerId;
    private static Integer contractorId;
    private static final String managerLogin = "testmanager-" + UUID.randomUUID();
    private static final String contractorLogin = "testcontractor-" + UUID.randomUUID();
    private static final String userPassword = "TestPassword123$";

    // Данные магазина
    private static Integer createdShopId;
    private static final String newShopName = "Test Shop " + UUID.randomUUID();
    private static final String updatedShopName = "Updated Shop " + UUID.randomUUID();


    @Test
    @Order(1)
    @DisplayName("1. [Setup] Вход в систему как администратор")
    void setup_LoginAsAdmin() {
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
    @DisplayName("2. [Setup] Создание пользователей (Manager и Contractor) для тестов")
    void setup_CreateTestUsers() {
        // Создание StoreManager
        CreateUserRequest managerRequest = new CreateUserRequest(managerLogin, userPassword, "StoreManager", "Test Manager", null, null);
        UserResponse managerResponse = webTestClient.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .bodyValue(managerRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        assertThat(managerResponse).isNotNull();
        storeManagerId = managerResponse.userID();

        // Создание Contractor
        CreateUserRequest contractorRequest = new CreateUserRequest(contractorLogin, userPassword, "Contractor", "Test Contractor", null, null);
        UserResponse contractorResponse = webTestClient.post().uri("/api/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .bodyValue(contractorRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        assertThat(contractorResponse).isNotNull();
        contractorId = contractorResponse.userID();
    }

    @Test
    @Order(3)
    @DisplayName("3. Администратор создает магазин (успешно, с StoreManager)")
    void adminCreatesShop_Success() {
        CreateShopRequest request = new CreateShopRequest(
                newShopName,
                "123 Test St, Test City",
                "shop@test.com",
                "123456789",
                storeManagerId // Назначаем валидного пользователя
        );

        ShopResponse response = webTestClient.post().uri("/api/admin/shops")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ShopResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.shopName()).isEqualTo(newShopName);
        assertThat(response.address()).isEqualTo("123 Test St, Test City");
        assertThat(response.userID()).isEqualTo(storeManagerId);
        assertThat(response.userLogin()).isEqualTo(managerLogin);
        assertThat(response.shopID()).isNotNull();

        createdShopId = response.shopID();
    }

    @Test
    @Order(4)
    @DisplayName("4. Администратор пытается создать магазин с существующим названием (конфликт)")
    void adminCreatesShop_WithExistingName_Conflict() {
        CreateShopRequest request = new CreateShopRequest(newShopName, null, null, null, null);

        webTestClient.post().uri("/api/admin/shops")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409); // Conflict
    }

    @Test
    @Order(5)
    @DisplayName("5. Администратор пытается создать магазин, назначив не-менеджера (запрещено)")
    void adminCreatesShop_WithNonManagerUser_Forbidden() {
        CreateShopRequest request = new CreateShopRequest(
                "Forbidden Shop",
                null, null, null,
                contractorId // Назначаем пользователя с ролью Contractor
        );

        webTestClient.post().uri("/api/admin/shops")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @Order(6)
    @DisplayName("6. Администратор получает список всех магазинов")
    void adminGetAllShops() {
        webTestClient.get().uri("/api/admin/shops")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ShopResponse.class)
                .value(shops -> {
                    assertThat(shops).isNotEmpty();
                    assertThat(shops.stream().anyMatch(s -> s.shopID().equals(createdShopId))).isTrue();
                });
    }

    @Test
    @Order(7)
    @DisplayName("7. Администратор обновляет данные магазина (успешно)")
    void adminUpdatesShop_Success() {
        UpdateShopRequest request = new UpdateShopRequest(
                updatedShopName,
                "456 Updated Ave, New City",
                "updated.shop@test.com",
                "987654321",
                null // Снимаем ответственного
        );

        ShopResponse response = webTestClient.put().uri("/api/admin/shops/" + createdShopId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ShopResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.shopName()).isEqualTo(updatedShopName);
        assertThat(response.address()).isEqualTo("456 Updated Ave, New City");
        assertThat(response.email()).isEqualTo("updated.shop@test.com");
        assertThat(response.userID()).isNull();
        assertThat(response.userLogin()).isEqualTo("N/A"); // Проверяем, что логин сбросился
    }

    @Test
    @Order(8)
    @DisplayName("8. Администратор пытается обновить магазин, установив уже занятое имя (конфликт)")
    void adminUpdatesShop_ToExistingName_Conflict() {
        // --- Setup: Создаем второй магазин ---
        String anotherShopName = "Another Test Shop " + UUID.randomUUID();
        CreateShopRequest anotherShopRequest = new CreateShopRequest(anotherShopName, null, null, null, null);
        ShopResponse anotherShop = webTestClient.post().uri("/api/admin/shops")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .bodyValue(anotherShopRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ShopResponse.class).returnResult().getResponseBody();
        assertThat(anotherShop).isNotNull();

        // --- Тест: Пытаемся первому магазину присвоить имя второго ---
        UpdateShopRequest updateRequest = new UpdateShopRequest(anotherShopName, null, null, null, null);

        webTestClient.put().uri("/api/admin/shops/" + createdShopId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isEqualTo(409); // Conflict

        // --- Cleanup: Удаляем второй магазин ---
        webTestClient.delete().uri("/api/admin/shops/" + anotherShop.shopID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();
    }


    @Test
    @Order(9)
    @DisplayName("9. Администратор удаляет магазин (успешно)")
    void adminDeletesShop_Success() {
        webTestClient.delete().uri("/api/admin/shops/" + createdShopId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @Order(10)
    @DisplayName("10. Проверка, что магазин был удален")
    void verifyShopDeletion() {
        webTestClient.get().uri("/api/admin/shops")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ShopResponse.class)
                .value(shops -> {
                    assertThat(shops.stream().noneMatch(s -> s.shopID().equals(createdShopId))).isTrue();
                });
    }

    @Test
    @Order(11)
    @DisplayName("11. [Cleanup] Удаление тестовых пользователей")
    void cleanup_DeleteTestUsers() {
        // Удаление StoreManager
        webTestClient.delete().uri("/api/admin/users/" + storeManagerId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();

        // Удаление Contractor
        webTestClient.delete().uri("/api/admin/users/" + contractorId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isNoContent();
    }
}