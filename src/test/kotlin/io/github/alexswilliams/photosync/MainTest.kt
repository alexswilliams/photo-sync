package io.github.alexswilliams.photosync

import aws.sdk.kotlin.runtime.auth.credentials.*
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.auth.awscredentials.*
import kotlinx.coroutines.*
import org.junit.Test
import java.nio.file.*
import kotlin.io.path.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class MainTest {
    val foldersToTearDown = mutableListOf<Path>()

    @Test
    fun test() {
        val archiveDir: Path = createTempDirectory("archive").also { foldersToTearDown.add(it) }
        val inboxDir: Path = createTempDirectory("inbox").also { foldersToTearDown.add(it) }
        val s3 = S3Fake("some-region", listOf("some-bucket"))
        val config: Config = object : Config {
            override val region = "some-region"
            override val bucketName = "some-bucket"
            override val s3Prefix = "some-prefix/"
            override val archivePath = archiveDir.toAbsolutePath().toString()
            override val inboxPath = inboxDir.toAbsolutePath().toString()
            override fun buildHttpEngine() = s3.httpClientEngine(listOf("some-access-key"))
            override fun buildCredentialsProvider() = CachedCredentialsProvider(StaticCredentialsProvider {
                accessKeyId = "some-access-key"
                secretAccessKey = "some-secret-access-key"
            })
        }

        val expectedMTime = Instant.parse("2025-02-23T09:35:00.2Z")
        val asEpochSecs = expectedMTime.toEpochMilliseconds().toBigDecimal().movePointLeft(3).stripTrailingZeros()
        s3.addFile(
            bucketName = "some-bucket",
            key = "test-file.txt",
            body = "Some test file".toByteArray(),
            storageClass = StorageClass.Standard,
            metadata = mapOf("mtime" to asEpochSecs.toPlainString())
        )

        runBlocking {
            main(config)
        }

        // Then the file has been moved to Glacier-IR
        val filesInS3 = s3.files("some-bucket")!!.entries
        assertTrue(filesInS3.size == 1)
        assertEquals("/test-file.txt", filesInS3.single().key)
        assertEquals(StorageClass.GlacierIr, filesInS3.single().value.storageClass)


        val filesInArchive = archiveDir.listDirectoryEntries().toList()
        val filesInInbox = inboxDir.listDirectoryEntries().toList()
        val fileInArchive = Path(archiveDir.toString(), "test-file.txt")
        val fileInInbox = Path(inboxDir.toString(), "test-file.txt")

        // Then the files have been copied to both the archive and the inbox
        assertContains(filesInArchive, fileInArchive)
        assertContains(filesInInbox, fileInInbox)
        assertEquals("Some test file", fileInArchive.readText(Charsets.UTF_8))
        assertEquals("Some test file", fileInInbox.readText(Charsets.UTF_8))

        // Then the files in both locations have had their file system modified time set to the epoch seconds value in the S3 file's metadata
        assertEquals(expectedMTime.toJavaInstant(), fileInArchive.getLastModifiedTime().toInstant())
        assertEquals(expectedMTime.toJavaInstant(), fileInInbox.getLastModifiedTime().toInstant())
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun teardown() {
        foldersToTearDown.forEach {
            it.deleteRecursively()
        }
        foldersToTearDown.clear()
    }
}
