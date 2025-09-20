package io.github.alexswilliams.photosync

import aws.sdk.kotlin.services.s3.*
import aws.sdk.kotlin.services.s3.model.*
import aws.sdk.kotlin.services.s3.paginators.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.io.path.*


internal suspend fun listNewFiles(
    s3: S3Client,
    bucketName: String,
    bucketPrefix: String,
    archivePath: String,
    inboxPath: String,
    decrypters: List<FileDecrypter>,
): List<FileInS3> = s3
    .listObjectsV2Paginated {
        bucket = bucketName
        prefix = bucketPrefix
    }
    .buffer(capacity = 1, onBufferOverflow = BufferOverflow.SUSPEND)
    .transform { page ->
        page.contents
            ?.filterNot { it.key == null }
            ?.filterNot { it.size == 0L }
            ?.filterNot { it.storageClass == StorageClass.GlacierIr }
            ?.filterNot { ".trashed" in (it.key!!) }
            ?.filterNot { ".empty" in (it.key!!) }
            ?.filterNot { ".nomedia" in (it.key!!) }
            ?.forEach { item -> this@transform.emit(item) }
    }
    .map {
        val (decryptedFilePath, decrypter) = decrypters.decryptFilePath(it.key!!)
        FileInS3(
            keyInBucket = it.key!!,
            pathInArchive = Path(archivePath, it.key!!).normalize(),
            pathInInbox = Path(inboxPath, decryptedFilePath).normalize(),
            decrypter = decrypter
        )
    }
    .filterNot { ".blank" in (it.pathInInbox.fileName.toString()) }
    .filterNot { ".trashed" in (it.pathInInbox.fileName.toString()) }
    .filterNot { ".empty" in (it.pathInInbox.fileName.toString()) }
    .filterNot { ".nomedia" in (it.pathInInbox.fileName.toString()) }
    .filterNot { it.pathInArchive.isHidden() }
    .filterNot { it.pathInArchive.exists() }
    .toList()

fun List<FileDecrypter>.decryptFilePath(key: String): Pair<String, FileDecrypter?> {
    this.forEach { fileDecrypter ->
        fileDecrypter.decryptPathOrNull(key)?.let {
            return it to fileDecrypter
        }
    }
    return key to null
}
