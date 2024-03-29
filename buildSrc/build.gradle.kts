plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.6.0"
}

repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:10.2.0")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.18.1")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.39.0")
}
