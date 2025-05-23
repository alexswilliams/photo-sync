package io.github.alexswilliams.photosync

import aws.sdk.kotlin.services.s3.*
import aws.smithy.kotlin.runtime.client.config.*
import kotlinx.coroutines.*


fun main(): Unit = runBlocking(Dispatchers.IO) {
    main(DefaultConfig)
}

internal suspend fun main(config: Config): Unit =
    config.buildHttpEngine()
        .use { httpClientEngine ->
            S3Client.fromEnvironment {
                credentialsProvider = config.buildCredentialsProvider()
                region = config.region
                httpClient = httpClientEngine
                responseChecksumValidation = ResponseHttpChecksumConfig.WHEN_REQUIRED
            }.use { s3Client ->
                val files = listNewFilesAndDispatchProcessor(
                    s3Client,
                    config.bucketName,
                    config.s3Prefix,
                    config.archivePath,
                    config.inboxPath
                )
                println("Found ${files.size} unseen files in S3")
                files.forEach { file ->
                    saveFileLocally(s3Client, config.bucketName, file)
                }
            }
        }
