package io.github.alexswilliams.photosync

import aws.sdk.kotlin.runtime.auth.credentials.*
import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.auth.awscredentials.*
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import java.nio.file.*
import kotlin.io.path.*
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
            override val decryptionPassword = "test-password"
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
        assertThat(filesInS3).hasSize(1)
        assertThat(filesInS3).singleElement().extracting { it.key }.isEqualTo("/test-file.txt")
        assertThat(filesInS3).singleElement().extracting { it.value.storageClass }.isSameAs(StorageClass.GlacierIr)


        val filesInArchive = archiveDir.listDirectoryEntries().toList()
        val filesInInbox = inboxDir.listDirectoryEntries().toList()
        val fileInArchive = Path(archiveDir.toString(), "test-file.txt")
        val fileInInbox = Path(inboxDir.toString(), "test-file.txt")

        // Then the files have been copied to both the archive and the inbox
        assertThat(filesInArchive).contains(fileInArchive)
        assertThat(filesInInbox).contains(fileInInbox)
        assertThat(fileInArchive).content(Charsets.UTF_8).isEqualTo("Some test file")
        assertThat(fileInInbox).content(Charsets.UTF_8).isEqualTo("Some test file")

        // Then the files in both locations have had their file system modified time set to the epoch seconds value in the S3 file's metadata
        assertThat(fileInArchive.getLastModifiedTime().toInstant()).isEqualTo(expectedMTime.toJavaInstant())
        assertThat(fileInInbox.getLastModifiedTime().toInstant()).isEqualTo(expectedMTime.toJavaInstant())
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterEach
    fun teardown() {
        foldersToTearDown.forEach {
            it.deleteRecursively()
        }
        foldersToTearDown.clear()
    }
}
