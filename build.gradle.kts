// https://gradle.org/releases/
// ./gradlew wrapper --gradle-version=8.14.3 --distribution-type=BIN

plugins {
    // https://mvnrepository.com/artifact/org.jetbrains.kotlin.jvm/org.jetbrains.kotlin.jvm.gradle.plugin
    kotlin("jvm") version "2.2.0"
    // https://github.com/graalvm/native-build-tools/releases
    id("org.graalvm.buildtools.native") version "0.10.6"
    application
}

group = "io.github.alexswilliams"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/aws.sdk.kotlin/s3
    val kotlinSdkVersion = "1.4.119"

    implementation("aws.sdk.kotlin:s3:$kotlinSdkVersion")
    implementation("aws.sdk.kotlin:s3control:$kotlinSdkVersion")

    // https://mvnrepository.com/artifact/aws.smithy.kotlin/http-client-engine-okhttp
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:1.4.22")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-simple
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

    // https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")

    testImplementation(kotlin("test"))
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
