import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
	java
	id("org.springframework.boot") version "4.0.0"
	id("io.spring.dependency-management") version "1.1.7"
    id("io.sentry.jvm.gradle") version "6.0.0"
}

group = "com.ghostchu.btn"
version = "0.0.1-SNAPSHOT"
description = "Sparkle3"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

sentry {
    // Enables more detailed log output, e.g. for sentry-cli.
    //
    // Default is false.
    debug = false
    // Generates a source bundle and uploads it to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    //
    // Default is disabled. To enable, see the source context guide.
    includeSourceContext = false
    // Disables or enables dependencies metadata reporting for Sentry.
    // If enabled, the plugin will collect external dependencies and
    // upload them to Sentry as part of events. If disabled, all the logic
    // related to the dependencies metadata report will be excluded.
    //
    // Default is enabled.
    includeDependenciesReport = false
    // Automatically adds Sentry dependencies to your project.
    autoInstallation {
        enabled = true
    }
    url = System.getenv("SENTRY_URL")
    org = System.getenv("SENTRY_ORG")
    projectName = System.getenv("SENTRY_PROJECT_SLUG")
    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("com.baomidou:mybatis-plus-bom:3.5.15")
    }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	//implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-mail")
//	implementation("org.springframework.boot:spring-boot-starter-quartz")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.apache.commons:commons-pool2")
	implementation("com.baomidou:mybatis-plus-spring-boot4-starter")
	implementation("com.baomidou:dynamic-datasource-spring-boot-starter:4.3.1")
	implementation("com.baomidou:lock4j-redis-template-spring-boot-starter:2.2.7")
	//implementation("com.baomidou:mybatis-plus-generator:3.5.14")
	//implementation("org.freemarker:freemarker:2.3.34")
	implementation("org.apache.kafka:kafka-streams")
	//implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
	compileOnly("org.projectlombok:lombok")
	compileOnly("org.jetbrains:annotations:26.0.2")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("org.postgresql:postgresql")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-batch-test")
	testImplementation("org.springframework.boot:spring-boot-starter-cache-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	//testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-quartz-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.20.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:2.20.1")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.20.1")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("com.vdurmont:semver4j:3.1.0")
    implementation("com.github.seancfoley:ipaddress:5.5.0")
    implementation("com.maxmind.geoip2:geoip2:4.2.0")
    implementation("org.kohsuke:github-api:1.330")
    implementation("com.baomidou:mybatis-plus-jsqlparser")
    implementation("com.github.haifengl:smile-core:5.1.0")
    implementation("com.clickhouse:clickhouse-jdbc:0.7.2")
    implementation(platform("io.sentry:sentry-bom:8.31.0")) //import bom
    implementation("io.sentry:sentry-async-profiler")
    implementation("io.sentry:sentry-logback");
    implementation("org.springframework.boot:spring-boot-starter-mail") // mailing

}

tasks.named<BootBuildImage>("bootBuildImage") {
	runImage = "paketobuildpacks/ubuntu-noble-run:latest"
}

tasks.named<Test>("test") {
	useJUnitPlatform()
}
