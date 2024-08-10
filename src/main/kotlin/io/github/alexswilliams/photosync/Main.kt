package io.github.alexswilliams.photosync

import aws.sdk.kotlin.runtime.auth.credentials.ProfileCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.copyObject
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.StorageClass
import aws.sdk.kotlin.services.s3.paginators.listObjectsV2Paginated
import aws.smithy.kotlin.runtime.auth.awscredentials.CachedCredentialsProvider
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.math.RoundingMode.DOWN
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.*

fun main(): Unit =
    runBlocking(Dispatchers.IO) {
        S3Client.fromEnvironment {
            credentialsProvider = CachedCredentialsProvider(ProfileCredentialsProvider(profileName = "s3sync", region = "eu-west-1"))
            region = "eu-west-1"
            httpClient(OkHttpEngine) {
                maxConcurrency = 12u
                maxConcurrencyPerHost = 12u
            }
        }.use { s3 ->
            fetchNewContent(s3, "various-barbed-earthworm", "pixel6/")
        }
    }

private data class FileInS3(val keyInBucket: String, val pathInArchive: Path, val pathInInbox: Path)

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun fetchNewContent(s3: S3Client, bucketName: String, prefix: String) = s3
    .listObjectsV2Paginated {
        this.bucket = bucketName
        this.prefix = prefix
    }
    .buffer()
    .transform { page ->
        page.contents
            ?.filter { it.key != null }
            ?.filterNot { ".trashed" in (it.key ?: "") }
            ?.filterNot { ".empty" in (it.key ?: "") }
            ?.filterNot { ".nomedia" in (it.key ?: "") }
            ?.forEach { item -> this.emit(item) }
    }
    .map {
        val subKey = it.key!!
        FileInS3(
            keyInBucket = it.key!!,
            pathInArchive = Path("/mnt/steam/photo-sync-inbox", subKey).normalize(),
            pathInInbox = Path("/mnt/steam/Photos/Inbox From Phone", subKey).normalize()
        )
    }
    .filterNot { it.pathInArchive.isHidden() }
    .filterNot { it.pathInArchive.exists() }
    .flatMapMerge(concurrency = 5) { file ->
        flow {
            saveToArchiveAndInboxOrThrow(file, s3, bucketName)
            emit(file)
        }
    }
    .count().also { println("Retrieved $it unseen files from S3") }


private suspend fun saveToArchiveAndInboxOrThrow(file: FileInS3, s3Client: S3Client, bucketName: String) {
    println("Fetching ${file.keyInBucket} to ${file.pathInArchive}")
    val (storageClass, metadata) = s3Client
        .getObject(GetObjectRequest { bucket = bucketName; key = file.keyInBucket }) { obj ->
            file.pathInArchive.createParentDirectories()
            obj.body?.writeToFile(file.pathInArchive) ?: throw NullPointerException("Body is null!")
            (obj.storageClass ?: StorageClass.Standard) to (obj.metadata ?: emptyMap())
        }

    println("Copying ${file.pathInArchive} to ${file.pathInInbox}")
    file.pathInInbox.createParentDirectories()
    file.pathInArchive.copyTo(file.pathInInbox, COPY_ATTRIBUTES, REPLACE_EXISTING)

    metadata["mtime"]?.toBigDecimalOrNull()?.let { preciseEpochSec ->
        val wholeSeconds = preciseEpochSec.setScale(0, DOWN)
        val nanoAdjustment = (preciseEpochSec - wholeSeconds).movePointRight(9)
        val modifiedAt = Instant.ofEpochSecond(wholeSeconds.toLong(), nanoAdjustment.toLong())
        println("Setting file time on new files for ${file.keyInBucket} to $modifiedAt")
        file.pathInArchive.setLastModifiedTime(FileTime.from(modifiedAt))
        file.pathInInbox.setLastModifiedTime(FileTime.from(modifiedAt))
    }

    if (storageClass == StorageClass.Standard) {
        println("Downgrading storage class for ${file.keyInBucket} to Glacier IR")
        val encodedUrl = withContext(Dispatchers.IO) {
            URLEncoder.encode("$bucketName/${file.keyInBucket}", StandardCharsets.UTF_8.toString())
        }
        s3Client.copyObject {
            copySource = encodedUrl
            bucket = bucketName
            key = file.keyInBucket
            this.metadata = metadata
            this.storageClass = StorageClass.GlacierIr
        }
    }
}

