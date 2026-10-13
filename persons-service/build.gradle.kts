import org.gradle.api.publish.maven.MavenPublication
import org.springframework.boot.gradle.tasks.bundling.BootJar

/*
 * person-service (модуль 2) на WebFlux + R2DBC
 * ─────────────────────────────────────────────────────────────
 *  • Spring Boot 4.0.7 / Java 25, реактивный HTTP (WebFlux) и неблокирующий доступ к БД (R2DBC)
 *  • Контракт-first: openapi/person-service.yaml → реактивные серверные интерфейсы (delegatePattern)
 *    и клиентский артефакт person-service-client (spring-http-interface, НЕ OpenFeign)
 *  • Flyway выполняется по JDBC на старте (для миграций это нормально), приложение работает по R2DBC
 *  • Аудит — собственный реактивный writer вместо Hibernate Envers:
 *    Envers является частью Hibernate ORM (блокирующий JPA) и с R2DBC несовместим
 *  • Публикация клиента в Nexus: snapshots/releases (group-адрес — на стороне потребителя)
 *
 * Генерация выполняется CLI-ядром openapi-generator 7.26.0, а не gradle-плагином:
 * плагин заморожен на 7.14.0 и не поддерживает useSpringBoot4.
 */

plugins {
    idea
    java
    jacoco
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
}

group = "net.example"
version = "1.0.0-SNAPSHOT"
description = "Person domain service on WebFlux + R2DBC: aggregate users + addresses + individuals"

val openApiGeneratorVersion = "7.26.0"
val springBootVersion = "4.0.7"
val logstashEncoderVersion = "9.0"
val jacocoVersion = "0.8.15"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// ============================================================
// OpenAPI generation
// ============================================================

val openApiGeneratorCli: Configuration by configurations.creating

val specFile = layout.projectDirectory.file("openapi/person-service.yaml")
val serverGenDir = layout.buildDirectory.dir("generated/openapi/server")
val clientGenDir = layout.buildDirectory.dir("generated/openapi/client")
val serverJavaDir = serverGenDir.map { it.dir("src/main/java") }
val clientJavaDir = clientGenDir.map { it.dir("src/main/java") }

// Клиентский код живёт в отдельном source set: он компилируется без основного кода
// и упаковывается в самостоятельный артефакт person-service-client.
val clientSourceSet = sourceSets.create("client") {
    java.srcDir(clientJavaDir)
}

sourceSets.named("main") {
    java.srcDir(serverJavaDir)
}

// ============================================================
// Dependencies
// ============================================================

dependencies {
    openApiGeneratorCli("org.openapitools:openapi-generator-cli:$openApiGeneratorVersion")

    // WEB — реактивный стек
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // PERSISTENCE — R2DBC как рабочий доступ к БД
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    implementation("org.postgresql:r2dbc-postgresql")

    // Flyway по JDBC: миграции выполняются синхронно при старте, до подъёма HTTP-сервера
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // OBSERVABILITY
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // CLIENT SOURCE SET — содержимое публикуемого артефакта person-service-client
    "clientImplementation"("org.springframework.boot:spring-boot-starter-restclient")
    "clientImplementation"("org.springframework.boot:spring-boot-starter-validation")

    // TEST
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // В Boot 4 поддержка WebTestClient вынесена в отдельный модуль
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ============================================================
// Generation tasks
// ============================================================

/*
 * Генератор всегда пишет вспомогательные файлы (свой pom, README, демо-приложение
 * org.openapitools.*, application.properties). Фильтр supportingFiles использовать нельзя:
 * вместе с мусором он выкидывает ApiUtil.java, на который ссылается сгенерированный delegate.
 * Поэтому генерируем всё и детерминированно вычищаем лишнее.
 */
fun JavaExec.removeGeneratedNoise(outDir: String) {
    doLast {
        listOf(
            "$outDir/pom.xml",
            "$outDir/README.md",
            "$outDir/.openapi-generator",
            "$outDir/.openapi-generator-ignore",
            "$outDir/src/test",
            "$outDir/src/main/resources",
            "$outDir/src/main/java/org"
        ).forEach { project.delete(it) }
    }
}

val generateServerApi by tasks.registering(JavaExec::class) {
    group = "openapi"
    description = "Генерирует реактивные серверные интерфейсы person-service в build/generated/openapi/server"

    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")

    inputs.file(specFile)
    outputs.dir(serverGenDir)

    val outDir = serverGenDir.get().asFile.absolutePath
    args(
        "generate",
        "-g", "spring",
        "-i", specFile.asFile.absolutePath,
        "-o", outDir,
        "--global-property", "apiDocs=false,modelDocs=false,apiTests=false,modelTests=false",
        "-p", "library=spring-boot," +
                "reactive=true," +
                "annotationLibrary=none," +
                "documentationProvider=none," +
                "interfaceOnly=false," +
                "delegatePattern=true," +
                "useSpringBoot4=true," +
                "useBeanValidation=true," +
                "openApiNullable=false," +
                "useTags=true," +
                "apiPackage=net.example.person.api," +
                "modelPackage=net.example.person.dto"
    )

    doFirst { project.delete(outDir) }
    removeGeneratedNoise(outDir)
}

val generateClientApi by tasks.registering(JavaExec::class) {
    group = "openapi"
    description = "Генерирует клиент person-service-client (HTTP Service Clients) в build/generated/openapi/client"

    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")

    inputs.file(specFile)
    outputs.dir(clientGenDir)

    val outDir = clientGenDir.get().asFile.absolutePath
    args(
        "generate",
        "-g", "spring",
        "-i", specFile.asFile.absolutePath,
        "-o", outDir,
        "--global-property", "apiDocs=false,modelDocs=false,apiTests=false,modelTests=false",
        "-p", "library=spring-http-interface," +
                "useSpringBoot4=true," +
                "useBeanValidation=true," +
                "openApiNullable=false," +
                "useTags=true," +
                "apiPackage=net.example.person.client.api," +
                "modelPackage=net.example.person.dto"
    )

    doFirst { project.delete(outDir) }
    removeGeneratedNoise(outDir)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateServerApi)
}

tasks.named<JavaCompile>("compileClientJava") {
    dependsOn(generateClientApi)
}

val generateOpenApi by tasks.registering {
    group = "openapi"
    description = "Генерирует серверный и клиентский код из OpenAPI-контракта"
    dependsOn(generateServerApi, generateClientApi)
}

// ============================================================
// Исполняемый артефакт сервиса
// ============================================================

tasks.named<BootJar>("bootJar") {
    // Детерминированное имя: Dockerfile копирует конкретный файл,
    // и в build/libs не появляется второй jar, ломающий COPY.
    archiveFileName.set("person-service.jar")
}

// Плоский jar не публикуется: единственный исполняемый артефакт — bootJar
tasks.named<Jar>("jar") {
    enabled = false
}

// ============================================================
// Публикуемый клиентский артефакт
// ============================================================

val clientJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Собирает person-service-client"
    archiveBaseName.set("person-service-client")
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(clientSourceSet.output)
    dependsOn(tasks.named("clientClasses"))
}

val clientSourcesJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Собирает person-service-client-sources"
    archiveBaseName.set("person-service-client")
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(clientSourceSet.allSource)
    dependsOn(generateClientApi)
}

tasks.named("assemble") {
    dependsOn(clientJar, clientSourcesJar)
}

// ============================================================
// Nexus publishing
// ============================================================

val dotEnv: Map<String, String> = file(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) null
        else trimmed.substringBefore("=").trim() to trimmed.substringAfter("=").trim()
    }
    ?.toMap()
    ?: emptyMap()

fun envOrProperty(name: String, default: String? = null): String? =
    System.getenv(name) ?: System.getProperty(name) ?: dotEnv[name] ?: default

val nexusBaseUrl = envOrProperty("NEXUS_BASE_URL", "http://localhost:8081/repository")!!
val nexusUsername = envOrProperty("NEXUS_USERNAME", "admin")
val nexusPassword = envOrProperty("NEXUS_PASSWORD", "admin")
val isSnapshotVersion = version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        create<MavenPublication>("personServiceClient") {
            artifactId = "person-service-client"
            artifact(clientJar)
            artifact(clientSourcesJar)

            pom {
                name.set("person-service-client")
                description.set("OpenAPI generated client for person-service (Spring HTTP Service Clients)")
                withXml {
                    // withXml вызывается несколько раз, поэтому манипуляция идемпотентна:
                    // иначе в POM появляются два dependencyManagement и он становится невалидным.
                    val root = asNode()
                    val existingNodes = root.children()
                        .filterIsInstance<groovy.util.Node>()
                        .map { it.name().toString() }
                        .toSet()

                    if ("dependencyManagement" !in existingNodes) {
                        val bom = root.appendNode("dependencyManagement")
                            .appendNode("dependencies")
                            .appendNode("dependency")
                        bom.appendNode("groupId", "org.springframework.boot")
                        bom.appendNode("artifactId", "spring-boot-dependencies")
                        bom.appendNode("version", springBootVersion)
                        bom.appendNode("type", "pom")
                        bom.appendNode("scope", "import")
                    }

                    if ("dependencies" !in existingNodes) {
                        val dependenciesNode = root.appendNode("dependencies")
                        listOf(
                            "org.springframework.boot" to "spring-boot-starter-restclient",
                            "org.springframework.boot" to "spring-boot-starter-validation"
                        ).forEach { (groupId, artifactId) ->
                            val dependency = dependenciesNode.appendNode("dependency")
                            dependency.appendNode("groupId", groupId)
                            dependency.appendNode("artifactId", artifactId)
                        }
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = if (isSnapshotVersion) "nexusSnapshots" else "nexusReleases"
            url = uri("$nexusBaseUrl/${if (isSnapshotVersion) "maven-snapshots" else "maven-releases"}")
            isAllowInsecureProtocol = true
            credentials {
                username = nexusUsername
                password = nexusPassword
            }
        }
    }
}

// ============================================================
// Tests и покрытие
// ============================================================

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

jacoco {
    toolVersion = jacocoVersion
}

// Сгенерированный код не измеряем: порог 80% относится к рукописному коду
val coverageExclusions = listOf(
    "net/example/person/api/**",
    "net/example/person/dto/**",
    "net/example/person/client/**",
    "org/openapitools/**"
)

fun ConfigurableFileCollection.withoutGenerated(): FileCollection =
    files(files.map { dir -> fileTree(dir) { exclude(coverageExclusions) } })

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(classDirectories.withoutGenerated())
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(classDirectories.withoutGenerated())
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.named<Test>("test") {
    finalizedBy(tasks.jacocoTestReport)
}
