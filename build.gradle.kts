import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.gradle.api.tasks.Exec

plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
    id("io.sentry.jvm.gradle") version "6.24.0"
}

group = "com.ghostchu.btn"
version = "0.0.1-SNAPSHOT"
description = "Sparkle3"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

configurations.configureEach {
	resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-pure-java") {
		select("at.yawk.lz4:lz4-java:1.10.1")
		because("Kafka and ClickHouse provide the same LZ4 capability; use Kafka's maintained implementation")
	}
}

dependencyManagement {
    imports {
        mavenBom("com.baomidou:mybatis-plus-bom:3.5.15")
    }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-aop")
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-mail")
//	implementation("org.springframework.boot:spring-boot-starter-quartz")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.apache.commons:commons-pool2")
	implementation("com.baomidou:mybatis-plus-spring-boot4-starter")
	implementation("com.baomidou:dynamic-datasource-spring-boot-starter:4.3.1")
	implementation("com.baomidou:lock4j-redis-template-spring-boot-starter:2.2.7")
	implementation("de.siegmar:fastcsv:4.4.0")
	implementation("com.maxmind.geoip2:geoip2:5.2.0")
	implementation("org.tukaani:xz:1.12")
	implementation("com.squareup.okhttp3:okhttp:5.5.1")
	implementation("org.apache.commons:commons-collections4:4.5.0")
	//implementation("com.baomidou:mybatis-plus-generator:3.5.14")
	//implementation("org.freemarker:freemarker:2.3.34")
	//implementation("org.apache.kafka:kafka-streams")
	implementation("org.flywaydb:flyway-database-postgresql")
	compileOnly("org.projectlombok:lombok")
	compileOnly("org.jetbrains:annotations:26.1.0")
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
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-quartz-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.22.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:2.22.4")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.22.4")
    implementation("com.google.guava:guava:33.7.1-jre")
    implementation("com.vdurmont:semver4j:3.1.0")
    implementation("com.github.seancfoley:ipaddress:5.6.2")
    implementation("com.maxmind.geoip2:geoip2:4.2.0")
    implementation("org.kohsuke:github-api:1.330")
    implementation("com.baomidou:mybatis-plus-jsqlparser")
    implementation("com.github.haifengl:smile-core:5.1.0")
    implementation("com.clickhouse:clickhouse-jdbc:0.7.2")
    implementation("org.springframework.boot:spring-boot-starter-mail") // mailing

}

tasks.named<BootBuildImage>("bootBuildImage") {
	runImage = "paketobuildpacks/ubuntu-noble-run:latest"
}

tasks.named<Test>("test") {
	useJUnitPlatform()
}

val frontendDirectory = layout.projectDirectory.dir("frontend")
val frontendBuildDirectory = layout.buildDirectory.dir("generated/frontend")
val pnpmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "pnpm.cmd" else "pnpm"

val installFrontendDependencies = tasks.register<Exec>("installFrontendDependencies") {
	group = "frontend"
	description = "Installs frontend dependencies with pnpm."
	workingDir(frontendDirectory)
	commandLine(pnpmExecutable, "install", "--frozen-lockfile")
	inputs.file(frontendDirectory.file("package.json"))
	inputs.file(frontendDirectory.file("pnpm-lock.yaml"))
	inputs.file(frontendDirectory.file("pnpm-workspace.yaml"))
	outputs.dir(frontendDirectory.dir("node_modules"))
}

val buildFrontend = tasks.register<Exec>("buildFrontend") {
	group = "frontend"
	description = "Builds the Vue frontend with pnpm."
	dependsOn(installFrontendDependencies)
	workingDir(frontendDirectory)
	commandLine(pnpmExecutable, "run", "build")
	inputs.files(
		fileTree(frontendDirectory) {
			include(
				"index.html",
				"package.json",
				"pnpm-lock.yaml",
				"tsconfig*.json",
				"vite.config.ts",
				"public/**",
				"src/**"
			)
			exclude("node_modules/**", "dist/**")
		}
	)
	outputs.dir(frontendBuildDirectory)
}

tasks.named<ProcessResources>("processResources") {
	dependsOn(buildFrontend)
	duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
	from(frontendBuildDirectory) {
		into("static")
	}
}
