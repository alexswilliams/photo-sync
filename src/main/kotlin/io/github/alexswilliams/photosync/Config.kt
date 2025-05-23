package io.github.alexswilliams.photosync

import aws.sdk.kotlin.runtime.auth.credentials.*
import aws.smithy.kotlin.runtime.auth.awscredentials.*
import aws.smithy.kotlin.runtime.http.engine.*
import aws.smithy.kotlin.runtime.http.engine.okhttp.*
import aws.smithy.kotlin.runtime.net.*
import kotlin.time.Duration.Companion.seconds


interface Config {
    val bucketName: String
    val s3Prefix: String
    val archivePath: String
    val inboxPath: String
    fun buildCredentialsProvider(): CloseableCredentialsProvider
    fun buildHttpEngine(): CloseableHttpClientEngine
}

internal object DefaultConfig : Config {
    override val bucketName: String get() = "various-barbed-earthworm"
    override val s3Prefix: String get() = "pixel6/"
    override val archivePath: String get() = "/mnt/steam/photo-sync-inbox"
    override val inboxPath: String get() = "/mnt/steam/Photos/Inbox From Phone"

    override fun buildCredentialsProvider() =
        CachedCredentialsProvider(ProfileCredentialsProvider(profileName = "s3sync", region = "eu-west-1"))

    override fun buildHttpEngine() = OkHttpEngine {
        maxConcurrency = 12u
        maxConcurrencyPerHost = 12u
        connectTimeout = 2.seconds
        tlsContext {
            minVersion = TlsVersion.TLS_1_3
        }
    }
}
