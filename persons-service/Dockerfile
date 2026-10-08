FROM gradle:9.5.1-jdk25 AS build

WORKDIR /home/gradle/project

# Сборка образа не публикует артефакты: за публикацию клиента в Nexus
# отвечает отдельная задача (./gradlew publish), ей нужны креды и доступ к Nexus.
COPY build.gradle.kts settings.gradle.kts ./
COPY openapi ./openapi
COPY src ./src

# Тесты запускаются отдельно (Testcontainers требует Docker), образ собираем без них.
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:25-jre

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Имя архива зафиксировано в build.gradle.kts (bootJar.archiveFileName),
# поэтому COPY не зависит от версии и не захватывает клиентский артефакт.
COPY --from=build /home/gradle/project/build/libs/person-service.jar app.jar

EXPOSE 8092

CMD ["java", "-jar", "app.jar"]
