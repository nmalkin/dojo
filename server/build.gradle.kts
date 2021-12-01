import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val ktorVersion = "1.6.6"
val exposedVersion = "0.36.2"

repositories {
    jcenter()
}

plugins {
    java
    application
    kotlin("plugin.serialization") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("scaffolding")
}

val main = "MainKt"

application {
    mainClassName = main // needed for shadow plugin
    mainClass.set(main)
}

dependencies {
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    runtimeOnly("org.xerial:sqlite-jdbc:3.36.0.3")
    runtimeOnly("org.postgresql:postgresql:42.3.1")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.30")
}

tasks.named<ShadowJar>("shadowJar").configure {
    archiveBaseName.set("dojo")
    archiveVersion.set("latest")
    archiveClassifier.set("server")
}
