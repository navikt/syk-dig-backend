import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask

plugins {
	id("org.springframework.boot") version "2.7.3"
	id("io.spring.dependency-management") version "1.0.13.RELEASE"
	kotlin("jvm") version "1.7.10"
	kotlin("plugin.spring") version "1.7.10"
	id("com.netflix.dgs.codegen") version "5.1.17"
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
val snakeYamlVersion = "1.31"
val smCommonVersion = "1.cbb3aed"
val testContainersVersion = "1.17.3"
val kluentVersion = "1.68"

dependencies {
	implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:latest.release"))
	implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.postgresql:postgresql:$postgresVersion")
	implementation("org.flywaydb:flyway-core")
	implementation("org.yaml:snakeyaml:$snakeYamlVersion") // overstyrer sårbar dependency
	implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
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