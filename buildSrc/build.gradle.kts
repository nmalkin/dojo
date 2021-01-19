plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.4.21"
}

repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.21")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:9.4.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.15.0")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.36.0")
}
