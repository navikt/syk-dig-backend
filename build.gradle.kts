import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.springframework.boot") version "2.7.5"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.spring") version "1.7.20"
    id("com.netflix.dgs.codegen") version "5.1.17"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
}

group = "no.nav.sykdig"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_17

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

val postgresVersion = "42.5.0"
val snakeYamlVersion = "1.33"
val smCommonVersion = "1.ea531b3"
val tokenSupportVersion = "2.1.7"
val testContainersVersion = "1.17.4"
val kluentVersion = "1.72"
val logstashLogbackEncoderVersion = "7.2"
val javaJwtVersion = "4.2.1"
val springBootResourceVersion = "2.7.5"
val graphqlVersion = "19.2"
val kafkaClientsVersion = "3.3.1"
val springSecurityWebVersion = "5.7.5"
val okhttp3version = "4.10.0"

dependencies {
    implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:latest.release"))
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter")
    implementation("com.netflix.graphql.dgs:graphql-dgs-extended-scalars")
    implementation("com.graphql-java:graphql-java:$graphqlVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.flywaydb:flyway-core")
    implementation("org.yaml:snakeyaml:$snakeYamlVersion") // overstyrer sårbar dependency
    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("com.auth0:java-jwt:$javaJwtVersion")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-web:$springSecurityWebVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:$springBootResourceVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttp3version")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.testcontainers:kafka:$testContainersVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<GenerateJavaTask> {
    packageName = "no.nav.sykdig.generated"
    generateClient = true
}

tasks.getByName<Jar>("jar") {
    enabled = false
}

configure<KtlintExtension> {
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}
