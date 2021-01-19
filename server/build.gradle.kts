project.version = "2021.01.18"

val ktorVersion = "1.5.0"
val exposedVersion = "0.28.1"

repositories {
    jcenter()
}

plugins {
    java
    application
    kotlin("plugin.serialization") version "1.4.21"
    id("com.github.johnrengelman.shadow") version "6.1.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
}
