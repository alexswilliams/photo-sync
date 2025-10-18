// https://gradle.org/releases/
// ./gradlew wrapper --gradle-version=9.1.0 --distribution-type=BIN  && ./gradlew wrapper

plugins {
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin.jvm/org.jetbrains.kotlin.jvm.gradle.plugin
    kotlin("jvm") version "2.2.20"
    // https://github.com/graalvm/native-build-tools/releases
    id("org.graalvm.buildtools.native") version "0.11.1"
    application
}

group = "io.github.alexswilliams"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/aws.sdk.kotlin/s3
    val kotlinSdkVersion = "1.5.63"

    implementation("aws.sdk.kotlin:s3:$kotlinSdkVersion")
    implementation("aws.sdk.kotlin:s3control:$kotlinSdkVersion")

    // https://mvnrepository.com/artifact/aws.smithy.kotlin/http-client-engine-okhttp
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:1.5.14")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    // https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // https://mvnrepository.com/artifact/org.bouncycastle/bcprov-jdk18on
    implementation("org.bouncycastle:bcprov-jdk18on:1.82")

    // https://mvnrepository.com/artifact/org.junit/junit-bom
    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.6")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("io.github.alexswilliams.photosync.MainKt")
}

graalvmNative {
    binaries.all {
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(24))
            vendor.set(JvmVendorSpec.GRAAL_VM)
        })
    }
    binaries.named("main") {
        imageName.set("photosync")
        buildArgs.add("-march=native")
    }
}
