package io.github.alexswilliams.photosync

import aws.sdk.kotlin.services.s3.*
import aws.smithy.kotlin.runtime.client.config.*
import kotlinx.coroutines.*
import java.time.*


fun main(): Unit = runBlocking(Dispatchers.IO) {
    main(DefaultConfig, InstantSource.system())
}

internal suspend fun main(config: Config, clock: InstantSource): Unit =
    config.buildHttpEngine()
        .use { httpClientEngine ->
            S3Client.fromEnvironment {
                credentialsProvider = config.buildCredentialsProvider()
                region = config.region
                httpClient = httpClientEngine
                responseChecksumValidation = ResponseHttpChecksumConfig.WHEN_REQUIRED
            }.use { s3Client ->
                val files = listNewFiles(
                    clock,
                    s3Client,
                    config.bucketName,
                    config.s3Prefix,
                    config.archivePath,
                    config.inboxPath,
                    config.decrypters
                )
                println("Found ${files.size} unseen files in S3")
                files.forEach { file ->
                    saveFileLocally(s3Client, config.bucketName, file)
                }
            }
        }
