import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("com.github.ben-manes.versions")
}

group = "io.nm.dojo"

repositories {
    mavenCentral()
    maven {
        url = uri("https://kotlin.bintray.com/kotlinx")
    }
}


dependencies {
    // JUnit
    testImplementation(kotlin("test-junit5"))
//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_14
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "14"
}

tasks.test {
    useJUnitPlatform()
}

tasks.getByPath("detekt").onlyIf { gradle.startParameter.taskNames.contains("detekt") }


ktlint {
    filter {
        exclude("build/**")
        exclude("**/generated/**")
        /*
        From https://github.com/jlleitschuh/ktlint-gradle#faq:
        Gradle based filtering are only working for files located inside project (subproject) folder, see https://github.com/gradle/gradle/issues/3417 To filter files outside project dir, use:
         */
        exclude { element -> element.file.path.contains("generated/") }
    }
}

detekt {
    toolVersion = "1.15.0"
    config = files("${rootDir}/detekt.yml")
    buildUponDefaultConfig = true
}
