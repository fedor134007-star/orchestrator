import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.gradle.api.publish.maven.MavenPublication


val versions = mapOf(
    "mapstructVersion" to "1.5.5.Final",
    "springdocOpenapiStarterWebmvcUiVersion" to "2.5.0",
    "javaxAnnotationApiVersion" to "1.3.2",
    "javaxValidationApiVersion" to "2.0.0.Final",
    "comGoogleCodeFindbugs" to "3.0.2",
    "springCloudStarterOpenfeign" to "4.1.1",
    "javaxServletApiVersion" to "2.5",
    "logbackClassicVersion" to "1.5.38",
    "comGoogleCodeFindbugs" to "3.0.2",
    "springCloudStarterOpenfeign" to "4.1.1",
    "hibernateEnversVersion" to "6.4.4.Final",
    "testContainersVersion" to "1.19.3",
    "junitJupiterVersion" to "5.10.0",
    "feignMicrometerVersion" to "13.6",
    "swagger" to "3.0.3",
    "webfluxTest" to "4.1.0",
    "keycloakTest" to "3.3.1"
)

plugins {
    idea
    java
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("org.openapi.generator") version "7.13.0"
}

group = "net.example"
version = "1.0.0-SNAPSHOT"
description = "Persons domain service for study project"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}


// ============================================
// Чтение .env файла
// ============================================
file(".env").takeIf { it.exists() }?.readLines()?.forEach { line ->
    val trimmed = line.trim()
    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
        val parts = trimmed.split("=", limit = 2)
        if (parts.size == 2) {
            val key = parts[0].trim()
            val value = parts[1].trim()
            System.setProperty(key, value)
            println("✅ Loaded env: ${key}=${value}")
        }
    }
}

// Получение значений с дефолтами
val nexusUrl = System.getProperty("NEXUS_URL")
    ?: System.getenv("NEXUS_URL")
    ?: "http://localhost:8081/repository/maven"

// Адрес для чтения зависимостей: group-репозиторий Nexus, агрегирующий
// hosted-репозитории (releases + snapshots) и proxy на Maven Central.
val nexusGroupUrl = System.getProperty("NEXUS_GROUP_URL")
    ?: System.getenv("NEXUS_GROUP_URL")
    ?: "http://localhost:8081/repository/maven-public"

val nexusUser = System.getProperty("NEXUS_USERNAME")
    ?: System.getenv("NEXUS_USERNAME")
    ?: "admin"

val nexusPassword = System.getProperty("NEXUS_PASSWORD")
    ?: System.getenv("NEXUS_PASSWORD")
    ?: "admin"

println("📦 NEXUS_URL: $nexusUrl")
println("👤 NEXUS_USERNAME: $nexusUser")

// ============================================
// Repositories
// ============================================
repositories {
    mavenCentral()

    // Единый групповой адрес Nexus: так потребитель получает и релизы, и снимки,
    // не зная, в каком именно hosted-репозитории они лежат.
    maven {
        name = "nexusGroup"
        url = uri(nexusGroupUrl)
        isAllowInsecureProtocol = true
        credentials {
            username = nexusUser
            password = nexusPassword
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.29.0")
    }
}

configurations.all { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }

dependencies {
    // SPRING
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:${versions["swagger"]}")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // OBSERVABILITY
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-observation")
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
    implementation("ch.qos.logback:logback-classic:${versions["logbackClassicVersion"]}")

    // HELPERS
    compileOnly("org.projectlombok:lombok")
    compileOnly("org.mapstruct:mapstruct:${versions["mapstructVersion"]}")
    compileOnly("com.google.code.findbugs:jsr305:${versions["comGoogleCodeFindbugs"]}")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:${versions["mapstructVersion"]}")
    implementation("javax.validation:validation-api:${versions["javaxValidationApiVersion"]}")
    implementation("javax.annotation:javax.annotation-api:${versions["javaxAnnotationApiVersion"]}")

    // TEST
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.junit.jupiter:junit-jupiter:${versions["junitJupiterVersion"]}")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.github.dasniko:testcontainers-keycloak:${versions["keycloakTest"]}")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-test")


    // PERSON SERVICE CLIENT (из Nexus): сгенерированный клиент на Spring HTTP Service Clients.
    // OpenFeign не используется — в Spring он считается функционально завершённым.
    implementation("net.example:person-service-client:1.0.0-SNAPSHOT")

}

tasks.withType<Test> {
    useJUnitPlatform()
}


/*
──────────────────────────────────────────────────────
============== Api generation ==============
──────────────────────────────────────────────────────
*/

val openApiDir = file("${rootDir}/openapi")

val foundSpecifications = openApiDir.listFiles { f -> f.extension in listOf("yaml", "yml") } ?: emptyArray()
logger.lifecycle("Found ${foundSpecifications.size} specifications: " + foundSpecifications.joinToString { it.name })

foundSpecifications.forEach { specFile ->
    val ourDir = getAbsolutePath(specFile.nameWithoutExtension)
    val packageName = defineJavaPackageName(specFile.nameWithoutExtension)

    val taskName = buildGenerateApiTaskName(specFile.nameWithoutExtension)
    logger.lifecycle("Register task ${taskName} from ${ourDir.get()}")
    val basePackage = "net.generated.${packageName}"

    tasks.register(taskName, GenerateTask::class) {
        generatorName.set("spring")
        inputSpec.set(specFile.absolutePath)
        outputDir.set(ourDir)

        configOptions.set(
            mapOf(
                "library" to "spring-boot",
                "reactive" to "true",
                "delegatePattern" to "false",
                "skipDefaultInterface" to "true",
                "useBeanValidation" to "true",
                "openApiNullable" to "false",
                "useFeignClientUrl" to "true",
                "useTags" to "true",
                "apiPackage" to "${basePackage}.api",
                "modelPackage" to "${basePackage}.dto",
                "configPackage" to "${basePackage}.config",
                "generateSupportingFiles" to "false",
                "interfaceOnly" to "true",
            )
        )

        doFirst {
            logger.lifecycle("$taskName: starting generation from ${specFile.name}")
        }
    }
}


fun getAbsolutePath(nameWithoutExtension: String): Provider<String> {
    return layout.buildDirectory
        .dir("generated-sources/openapi/${nameWithoutExtension}")
        .map { it.asFile.absolutePath }
}

fun defineJavaPackageName(name: String): String {
    val beforeDash = name.substringBefore('-')
    val match = Regex("^[a-z]+").find(beforeDash)
    return match?.value ?: beforeDash.lowercase()
}

fun buildGenerateApiTaskName(name: String): String {
    return buildTaskName("generate", name)
}

fun buildJarTaskName(name: String): String {
    return buildTaskName("jar", name)
}

fun buildTaskName(taskPrefix: String, name: String): String {
    val prepareName = name
        .split(Regex("[^A-Za-z0-9]"))
        .filter { it.isNotBlank() }
        .joinToString("") { it.replaceFirstChar(Char::uppercase) }

    return "${taskPrefix}-${prepareName}"
}

val withoutExtensionNames = foundSpecifications.map { it.nameWithoutExtension }

sourceSets.named("main") {
    withoutExtensionNames.forEach { name ->
        java.srcDir(layout.buildDirectory.dir("generated-sources/openapi/$name/src/main/java"))
    }
}

tasks.register("generateAllOpenApi") {
    foundSpecifications.forEach { specFile ->
        dependsOn(buildGenerateApiTaskName(specFile.nameWithoutExtension))
    }
    doLast {
        logger.lifecycle("generateAllOpenApi: all specifications has been generated")
    }
}

tasks.named("compileJava") {
    dependsOn("generateAllOpenApi")
}

/*
──────────────────────────────────────────────────────
============== Building jars ==============
──────────────────────────────────────────────────────
*/

tasks.named("build") {
    dependsOn(generatedJars)
}

val generatedJars = foundSpecifications.map { specFile ->
    val name = specFile.nameWithoutExtension
    val generateTaskName = buildGenerateApiTaskName(name)
    val jarTaskName = buildJarTaskName(name)
    val outDirProvider = getAbsolutePath(name)
    val generateSrcDir = outDirProvider.map { File(it).resolve("src/main/java") }

    val sourcesSetName = name

    val sourceSet = sourceSets.create(sourcesSetName) {
        java.srcDir(generateSrcDir)
        compileClasspath += sourceSets["main"].compileClasspath
    }

    val compileTaskName = "compile${sourcesSetName.replaceFirstChar(Char::uppercase)}Java"
    tasks.register<JavaCompile>(compileTaskName) {
        source = sourceSet.java
        classpath = sourceSet.compileClasspath
        destinationDirectory.set(layout.buildDirectory.dir("classes/${sourcesSetName}"))
        dependsOn(generateTaskName)
    }

    tasks.register<Jar>(jarTaskName) {
        group = "build"
        archiveBaseName.set(name)
        destinationDirectory.set(layout.buildDirectory.dir("libs"))

        val classOutput = layout.buildDirectory.dir("classes/${sourcesSetName}")
        from(classOutput)
        dependsOn(compileTaskName)

        doFirst {
            println("Building JAR for $name from compiled classes in ${classOutput.get().asFile}")
        }
    }
}

/*
──────────────────────────────────────────────────────
============== Resolve NEXUS credentials ==============
──────────────────────────────────────────────────────
*/

file(".env").takeIf { it.exists() }?.readLines()?.forEach {
    val (k, v) = it.split("=", limit = 2)
    System.setProperty(k.trim(), v.trim())
    logger.lifecycle("${k.trim()}=${v.trim()}")
}

/*
──────────────────────────────────────────────────────
============== Nexus Publishing ==============
──────────────────────────────────────────────────────
*/

publishing {
    publications {
        foundSpecifications.forEach { specFile ->
            val name = specFile.nameWithoutExtension
            val jarBaseName = name
            var jarFile = file("build/libs")
                .listFiles()
                ?.firstOrNull { it.name.contains(name) && (it.extension == "jar" || it.extension == "zip") }

            if (jarFile != null) {
                logger.lifecycle("publishing: ${jarFile.name}")

                create<MavenPublication>("publish${name.replaceFirstChar(Char::uppercase)}Jar") {
                    artifact(jarFile)
                    groupId = "net.proselyte"
                    artifactId = jarBaseName
                    version = "1.0.0-SNAPSHOT"

                    pom {
                        this.name.set("Generated API $jarBaseName")
                        this.description.set("OpenAPI generated code for $jarBaseName")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "nexus"
            url = uri(nexusUrl)
            isAllowInsecureProtocol = true
            credentials {
                username = nexusUser
                password = nexusPassword
            }
        }
    }
}