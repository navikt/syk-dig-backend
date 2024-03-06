import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.springframework.boot") version "3.2.3"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    id("com.netflix.dgs.codegen") version "5.12.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("org.cyclonedx.bom") version "1.8.2"
}

group = "no.nav.sykdig"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

val postgresVersion = "42.7.2"
val snakeYamlVersion = "2.2"
val diagnosekoderVersion = "1.2024.0"
val tokenSupportVersion = "4.1.3"
val testContainersVersion = "1.19.7"
val logstashLogbackEncoderVersion = "7.4"
val javaJwtVersion = "4.4.0"
val springBootResourceVersion = "3.2.3"
val graphqlVersion = "20.2"
val kafkaClientsVersion = "3.7.0"
val syfoXmlCodegen = "2.0.1"
val springSecurityWebVersion = "6.2.2"
val okhttp3version = "4.12.0"
val jaxbApiVersion = "2.3.1"
val jaxbVersion = "2.4.0-b180830.0438"
val javaxActivationVersion = "1.1.1"
val javaTimeAdapterVersion = "1.1.3"
val graphqlDgsPlatformDependenciesVersion = "7.3.6"
val logbacksyslog4jVersion = "1.0.0"

dependencies {
    implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:$graphqlDgsPlatformDependenciesVersion"))
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
    implementation("com.papertrailapp:logback-syslog4j:$logbacksyslog4jVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:$testContainersVersion")
    testImplementation("org.testcontainers:kafka:$testContainersVersion")
}

tasks {
    withType<Test> {
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

    getByName<Jar>("jar") {
        enabled = false
    }
    "compileKotlin" {
        dependsOn("ktlintFormat")
    }

    "check" {
        dependsOn("ktlintCheck")
    }
}
configure<KtlintExtension> {
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}
