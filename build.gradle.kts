// https://gradle.org/releases/
// ./gradlew wrapper --gradle-version=8.12 --distribution-type=BIN

plugins {
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin.jvm/org.jetbrains.kotlin.jvm.gradle.plugin
    kotlin("jvm") version "2.1.0"
    application
}

group = "io.github.alexswilliams"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/aws.sdk.kotlin/s3
    val kotlinSdkVersion = "1.3.106"

    implementation("aws.sdk.kotlin:s3:$kotlinSdkVersion")
    implementation("aws.sdk.kotlin:s3control:$kotlinSdkVersion")

    // https://mvnrepository.com/artifact/aws.smithy.kotlin/http-client-engine-okhttp
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:1.3.32")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    val kotlinCoroutinesVersion = "1.10.1"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    // https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
}

kotlin {
    jvmToolchain(21)
}

application {
    this.mainClass.set("io.github.alexswilliams.photosync.MainKt")
}
