# Сборка
# БЫЛО: FROM maven:3.9.6-eclipse-temurin-17 AS build
# СТАЛО:
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Пропускаем тесты при сборке для ускорения
RUN mvn clean package -Dmaven.test.skip=true

# Запуск
# БЫЛО: FROM eclipse-temurin:17-jre-alpine
# СТАЛО:
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]