import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("org.springframework.boot") version "3.1.2"
    id("io.spring.dependency-management") version "1.1.2"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.spring") version "1.9.0"
    id("com.netflix.dgs.codegen") version "5.12.4"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.0"
    id("org.cyclonedx.bom") version "1.7.4"
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

val postgresVersion = "42.6.0"
val snakeYamlVersion = "2.0"
val smCommonVersion = "1.0.6"
val tokenSupportVersion = "3.1.0"
val testContainersVersion = "1.18.3"
val logstashLogbackEncoderVersion = "7.4"
val javaJwtVersion = "4.4.0"
val springBootResourceVersion = "3.1.1"
val graphqlVersion = "20.2"
val kafkaClientsVersion = "3.5.0"
val syfoXmlCodegen = "1.0.4"
val springSecurityWebVersion = "6.1.2"
val okhttp3version = "4.10.0"
val jaxbApiVersion = "2.3.1"
val jaxbVersion = "2.4.0-b180830.0438"
val javaxActivationVersion = "1.1.1"
val javaTimeAdapterVersion = "1.1.3"
val graphqlDgsPlatformDependenciesVersion = "7.3.2"
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
    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:sm2013:$syfoXmlCodegen")
    implementation("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegen")
    implementation("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonVersion")
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
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

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

    "check" {
        dependsOn("ktlintFormat")
    }
}
configure<KtlintExtension> {
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}
