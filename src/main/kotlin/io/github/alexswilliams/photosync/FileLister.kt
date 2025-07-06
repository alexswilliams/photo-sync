package io.github.alexswilliams.photosync

import aws.sdk.kotlin.services.s3.*
import aws.sdk.kotlin.services.s3.model.StorageClass
import aws.sdk.kotlin.services.s3.paginators.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.io.path.*


internal suspend fun listNewFilesAndDispatchProcessor(
    s3: S3Client,
    bucketName: String,
    bucketPrefix: String,
    archivePath: String,
    inboxPath: String,
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
        FileInS3(
            keyInBucket = it.key!!,
            pathInArchive = Path(archivePath, it.key!!).normalize(),
            pathInInbox = Path(inboxPath, it.key!!).normalize()
        )
    }
    .filterNot { it.pathInArchive.isHidden() }
    .filterNot { it.pathInArchive.exists() }
    .toList()
