# Руководство по системе логирования и аудита

## Обзор

В проекте реализована полная система логирования и аудита действий пользователей. Система автоматически логирует все HTTP-запросы, ошибки и действия пользователей в базе данных.

## Компоненты системы

### 1. Логирование приложения (ApplicationLog)

**Таблица:** `ApplicationLog`

Автоматически логирует:
- Все HTTP-запросы (через `LoggingWebFilter`)
- Все ошибки (через `GlobalExceptionHandler`)
- Информационные сообщения, предупреждения и ошибки

**Уровни логирования:**
- `INFO` - информационные сообщения
- `WARN` - предупреждения
- `ERROR` - ошибки
- `DEBUG` - отладочная информация

### 2. Аудит действий (AuditLog)

**Таблица:** `AuditLog`

Логирует все изменения данных:
- `CREATE` - создание записей
- `UPDATE` - обновление записей
- `DELETE` - удаление записей

## Использование

### Автоматическое логирование

Система автоматически логирует:
- Все HTTP-запросы через `LoggingWebFilter`
- Все ошибки через `GlobalExceptionHandler`

### Ручное логирование

Для ручного логирования используйте `LoggingService`:

```java
@Autowired
private LoggingService loggingService;

// Логирование информации
loggingService.logInfo("MyService", "Операция выполнена", userID, userLogin, 
    ipAddress, userAgent, endpoint, method).subscribe();

// Логирование ошибки
loggingService.logError("MyService", "Ошибка выполнения", exception, 
    userID, userLogin, ipAddress, userAgent, endpoint, method).subscribe();
```

### Аудит действий

Для аудита действий используйте `AuditHelper`:

```java
@Autowired
private AuditHelper auditHelper;

// Аудит создания
@PostMapping
public Mono<ResponseEntity<ShopResponse>> createShop(
        @RequestBody CreateShopRequest request, 
        ServerWebExchange exchange) {
    return shopService.createShop(request)
        .flatMap(shop -> {
            auditHelper.auditCreate("Shops", shop.getShopID(), shop, exchange)
                .subscribe();
            return Mono.just(shop);
        });
}

// Аудит обновления
@PutMapping("/{id}")
public Mono<ResponseEntity<ShopResponse>> updateShop(
        @PathVariable Integer id,
        @RequestBody UpdateShopRequest request,
        ServerWebExchange exchange) {
    // Получаем старую версию
    ShopResponse oldShop = ...;
    
    return shopService.updateShop(id, request)
        .flatMap(updatedShop -> {
            auditHelper.auditUpdate("Shops", id, oldShop, updatedShop, exchange)
                .subscribe();
            return Mono.just(updatedShop);
        });
}

// Аудит удаления
@DeleteMapping("/{id}")
public Mono<ResponseEntity<Void>> deleteShop(
        @PathVariable Integer id,
        ServerWebExchange exchange) {
    // Получаем удаляемую запись
    ShopResponse shopToDelete = ...;
    
    return shopService.deleteShop(id)
        .then(Mono.fromRunnable(() -> {
            auditHelper.auditDelete("Shops", id, shopToDelete, exchange)
                .subscribe();
        }))
        .thenReturn(ResponseEntity.noContent().build());
}
```

## Просмотр логов и аудита

### Фронтенд интерфейс

1. **Логи приложения:** `/logs` (только для RetailAdmin)
   - Фильтрация по уровню, пользователю, дате
   - Статистика по уровням логов
   - Просмотр деталей ошибок

2. **Аудит действий:** `/audit` (только для RetailAdmin)
   - Фильтрация по таблице, действию, пользователю, дате
   - Статистика по типам действий
   - Просмотр изменений (старое/новое значение)

### API Endpoints

**Логи:**
- `GET /api/admin/logs` - получение логов с фильтрацией
- `GET /api/admin/logs/stats` - статистика по логам

**Аудит:**
- `GET /api/admin/audit` - получение записей аудита с фильтрацией
- `GET /api/admin/audit/stats` - статистика по аудиту

## Автоматическая очистка

Система автоматически очищает старые записи:

- **Логи приложения:** записи старше 90 дней (настраивается через `logging.retention.days`)
- **Записи аудита:** записи старше 365 дней (настраивается через `audit.retention.days`)

Очистка выполняется каждый день в 2:00 ночи через `LogCleanupService`.

## Настройка

Добавьте в `application.properties` или `application.yml`:

```properties
# Срок хранения логов (в днях)
logging.retention.days=90

# Срок хранения записей аудита (в днях)
audit.retention.days=365
```

## Интеграция в существующие контроллеры

Пример интеграции аудита в `ShopController`:

1. Добавьте `AuditHelper` в конструктор
2. Вызовите соответствующий метод аудита после операции
3. Передайте `ServerWebExchange` в метод контроллера

См. `ShopController.java` для полного примера.

## Важные замечания

1. **Асинхронность:** Все методы логирования и аудита возвращают `Mono<Void>` и выполняются асинхронно. Используйте `.subscribe()` для запуска.

2. **Производительность:** Логирование не блокирует основной поток выполнения. Ошибки логирования не влияют на работу приложения.

3. **Безопасность:** Доступ к логам и аудиту имеют только пользователи с ролью `RetailAdmin`.

4. **Хранение:** Все логи и записи аудита хранятся в базе данных SQL Server в таблицах `ApplicationLog` и `AuditLog`.

