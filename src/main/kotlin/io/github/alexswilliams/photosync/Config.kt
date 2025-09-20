package io.github.alexswilliams.photosync

import aws.sdk.kotlin.runtime.auth.credentials.*
import aws.smithy.kotlin.runtime.auth.awscredentials.*
import aws.smithy.kotlin.runtime.http.engine.*
import aws.smithy.kotlin.runtime.http.engine.okhttp.*
import aws.smithy.kotlin.runtime.net.*
import java.io.*
import kotlin.time.Duration.Companion.seconds


interface Config {
    val region: String
    val bucketName: String
    val s3Prefix: String
    val archivePath: String
    val inboxPath: String
    val decrypters: List<FileDecrypter>
    fun buildCredentialsProvider(): CloseableCredentialsProvider
    fun buildHttpEngine(): CloseableHttpClientEngine
}

internal object DefaultConfig : Config {
    override val region = "eu-west-1"
    override val bucketName = "various-barbed-earthworm"
    override val s3Prefix = "pixel6/"
    override val archivePath = "/mnt/steam/photo-sync-inbox"
    override val inboxPath = "/mnt/steam/Photos/Inbox From Phone"
    override val decrypters = listOf(
        RCryptDecrypter(File("~/.s3encpwd").readLines().first().trim())
    )

    override fun buildCredentialsProvider() =
        CachedCredentialsProvider(ProfileCredentialsProvider(profileName = "s3sync", region = region))

    override fun buildHttpEngine() = OkHttpEngine {
        maxConcurrency = 12u
        maxConcurrencyPerHost = 12u
        connectTimeout = 2.seconds
        tlsContext {
            minVersion = TlsVersion.TLS_1_3
        }
    }
}
