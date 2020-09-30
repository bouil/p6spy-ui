plugins {
    val kotlinVersion = "1.4.10"
    kotlin("jvm") version kotlinVersion
    `java-library`
    id("org.springframework.boot") version "2.3.4.RELEASE"
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    id("io.spring.dependency-management") version "1.0.8.RELEASE"
    id("maven-publish")
    id("org.openjfx.javafxplugin") version "0.0.9"
}

apply(plugin = "io.spring.dependency-management")

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

group = "net.bouillon"
version = "1.0.0-SNAPSHOT"
description = "net.bouillon p6spy-ui"

dependencies {
    implementation("io.reactivex.rxjava3:rxjava:3.0.6")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.0")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.10.4")
    implementation("net.bouillon:p6spy-socket:1.0.0-SNAPSHOT")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.4.0")
    testImplementation("junit:junit:4.12")
}

tasks.withType(JavaCompile::class) {
    sourceCompatibility = "11"
    options.encoding = "UTF-8"
}

javafx {
    modules("javafx.graphics", "javafx.controls", "javafx.fxml")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
