import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    id("com.netflix.dgs.codegen") version "5.12.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav.sykdig"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

val postgresVersion = "42.7.4"
val snakeYamlVersion = "2.3"
val diagnosekoderVersion = "1.2024.1"
val tokenSupportVersion = "5.0.5"
val testContainersVersion = "1.20.3"
val logstashLogbackEncoderVersion = "8.0"
val javaJwtVersion = "4.4.0"
val springBootResourceVersion = "3.3.4"
val graphqlVersion = "20.2"
val kafkaClientsVersion = "3.8.0"
val syfoXmlCodegen = "2.0.1"
val springSecurityWebVersion = "6.3.4"
val okhttp3version = "4.12.0"
val jaxbApiVersion = "2.3.1"
val jaxbVersion = "2.4.0-b180830.0438"
val javaxActivationVersion = "1.1.1"
val javaTimeAdapterVersion = "1.1.3"
val graphqlDgsPlatformDependenciesVersion = "7.3.6"
val logbacksyslog4jVersion = "1.0.0"
val commonsCompressVersion = "1.27.1"
val commonsLang3Version = "3.17.0"
val httpClient5version = "5.4"
val flywayVersion = "10.20.0"
val opentelemetryVersion = "2.9.0"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.13.13"
val kluentVersion = "1.73"
val coroutinesVersion = "1.9.0"

dependencies {
    implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:$graphqlDgsPlatformDependenciesVersion"))
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter")
    implementation("com.netflix.graphql.dgs:graphql-dgs-extended-scalars")
    implementation("com.graphql-java:graphql-java:$graphqlVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations")
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
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.yaml:snakeyaml:$snakeYamlVersion") // overstyrer sårbar dependency
    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegen")
    implementation("no.nav.helse:diagnosekoder:$diagnosekoderVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("com.auth0:java-jwt:$javaJwtVersion")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-web:$springSecurityWebVersion")
    implementation("no.nav.security:token-client-spring:$tokenSupportVersion")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:$springBootResourceVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttp3version")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("com.papertrailapp:logback-syslog4j:$logbacksyslog4jVersion")
    implementation("no.nav.security:token-support:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-core:$tokenSupportVersion")
    implementation("no.nav.security:token-validation-spring:$tokenSupportVersion")
    implementation("org.apache.commons:commons-lang3:$commonsLang3Version")
    implementation("org.apache.httpcomponents.client5:httpclient5:$httpClient5version")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.testcontainers:kafka:$testContainersVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    constraints {
        testImplementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("overstyrer sårbar dependency fra com.opentable.components:otj-pg-embedded")
        }
    }
}

tasks {

    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.sykdig.SykDigBackendApplication.kt",
                ),
            )
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    withType<GenerateJavaTask> {
        packageName = "no.nav.sykdig.generated"
        generateClient = true
    }

    jar {
        enabled = false
    }
    "compileKotlin" {
        dependsOn("ktlintFormat")
    }

    check {
        dependsOn("ktlintCheck")
    }
}
configure<KtlintExtension> {
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}
