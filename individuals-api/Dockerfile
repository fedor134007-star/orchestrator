FROM gradle:9.5.1-jdk25 AS build
# NEXUS_URL — hosted-репозиторий (публикация), NEXUS_GROUP_URL — групповой адрес для чтения
# зависимостей: именно оттуда забирается артефакт person-service-client.
ARG NEXUS_URL=http://host.docker.internal:8081/repository/maven
ARG NEXUS_GROUP_URL=http://host.docker.internal:8081/repository/maven-public
ARG NEXUS_USERNAME=admin
ARG NEXUS_PASSWORD=admin

ENV NEXUS_URL=${NEXUS_URL}
ENV NEXUS_GROUP_URL=${NEXUS_GROUP_URL}
ENV NEXUS_USERNAME=${NEXUS_USERNAME}
ENV NEXUS_PASSWORD=${NEXUS_PASSWORD}

WORKDIR /home/gradle/project

COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src ./src
COPY openapi ./openapi

# Создаем bootJar
RUN gradle --no-daemon clean bootJar -x test

# Находим bootJar и переименовываем в app.jar
RUN find build/libs -name "*.jar" ! -name "*plain.jar" ! -name "*javadoc.jar" ! -name "*sources.jar" -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:25-jre

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем из /tmp
COPY --from=build /tmp/app.jar app.jar

EXPOSE 8091
CMD ["java", "-jar", "app.jar"]