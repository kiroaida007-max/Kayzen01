plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dz.wave"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.6.0"

dependencies {
    // HTTP server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-conditional-headers-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-body-limit-jvm:$ktorVersion")

    // HTTP client (AIS stream, payment gateway, rates)
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Persistence, cache, messaging
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.flywaydb:flyway-core:13.9.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.9.0")
    implementation("io.lettuce:lettuce-core:7.8.0.RELEASE")
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.0")

    // Observability and crypto
    implementation("ch.qos.logback:logback-classic:1.6.5")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.17.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(false)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

application {
    mainClass.set("dz.wave.ApplicationKt")
    applicationDefaultJvmArgs = listOf("-XX:+UseZGC", "-XX:MaxRAMPercentage=75")
}

tasks.test {
    useJUnitPlatform()
    // Integration tests opt in through WAVE_TEST_DATABASE_URL / WAVE_TEST_REDIS_URL.
    environment("WAVE_ENV", "test")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.shadowJar {
    archiveFileName.set("wave-backend.jar")
    mergeServiceFiles()
}
