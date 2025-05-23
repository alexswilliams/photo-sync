package io.github.alexswilliams.photosync

import aws.sdk.kotlin.services.s3.*
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.content.*
import java.math.BigDecimal
import java.math.RoundingMode.*
import java.net.*
import java.nio.charset.*
import java.nio.file.*
import java.nio.file.StandardCopyOption.*
import java.nio.file.attribute.*
import java.time.*
import kotlin.io.path.*


internal data class FileInS3(val keyInBucket: String, val pathInArchive: Path, val pathInInbox: Path)

internal suspend fun saveFileLocally(
    s3Client: S3Client,
    bucketName: String,
    file: FileInS3,
) {
    println("Fetching ${file.keyInBucket} to ${file.pathInArchive}")
    val (storageClass, modifiedTimeEpochSecs) = s3Client.fetchToFile(bucketName, file)

    println("Copying ${file.pathInArchive} to ${file.pathInInbox}")
    copyToInbox(file.pathInArchive, file.pathInInbox)

    modifiedTimeEpochSecs?.let { mtime ->
        setFileModifiedTime(file.pathInInbox, mtime)
        setFileModifiedTime(file.pathInArchive, mtime)
    }

    if (storageClass == StorageClass.Standard) {
        println("Downgrading storage class for ${file.keyInBucket} to Glacier IR")
        s3Client.changeStorageClass(bucketName, file.keyInBucket, StorageClass.GlacierIr)
    }
}

private suspend fun S3Client.fetchToFile(bucketName: String, file: FileInS3): Pair<StorageClass, BigDecimal?> =
    getObject(GetObjectRequest {
        bucket = bucketName
        key = file.keyInBucket
        checksumMode = null
    }) { obj ->
        file.pathInArchive.createParentDirectories()
        obj.body?.writeToFile(file.pathInArchive) ?: throw NullPointerException("Body is null!")
        (obj.storageClass ?: StorageClass.Standard) to (obj.metadata?.get("mtime")?.toBigDecimalOrNull())
    }

private fun copyToInbox(pathInArchive: Path, pathInInbox: Path) {
    pathInInbox.createParentDirectories()
    pathInArchive.copyTo(pathInInbox, COPY_ATTRIBUTES, REPLACE_EXISTING)
}

private fun setFileModifiedTime(path: Path, secondsSinceEpoch: BigDecimal) {
    val wholeSeconds = secondsSinceEpoch.setScale(0, DOWN)
    val nanoAdjustment = (secondsSinceEpoch - wholeSeconds).movePointRight(9)
    val modifiedAt = Instant.ofEpochSecond(wholeSeconds.toLong(), nanoAdjustment.toLong())
    println("Setting file time on $path to $modifiedAt")
    path.setLastModifiedTime(FileTime.from(modifiedAt))
}

private suspend fun S3Client.changeStorageClass(
    bucketName: String,
    keyInBucket: String,
    newStorageClass: StorageClass,
) {
    copyObject {
        copySource = URLEncoder.encode("$bucketName/$keyInBucket", StandardCharsets.UTF_8.toString())
        bucket = bucketName
        key = keyInBucket
        metadataDirective = MetadataDirective.Copy
        taggingDirective = TaggingDirective.Copy
        storageClass = newStorageClass
    }
}
