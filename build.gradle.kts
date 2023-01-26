import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.0"
}

group = "io.github.mathmaster13.avcbasic"
version = "0.1-SNAPSHOT"

// default code that I don't need
//repositories {
//    mavenCentral()
//}
//
//dependencies {
//    testImplementation(kotlin("test"))
//}
//
//tasks.test {
//    useJUnitPlatform()
//}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
    kotlinOptions.languageVersion = "1.9" // experimental APIs
}
dependencies {
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions.freeCompilerArgs.add("-progressive")
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "17"
}