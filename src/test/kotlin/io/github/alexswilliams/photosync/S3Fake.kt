package io.github.alexswilliams.photosync

import aws.sdk.kotlin.services.s3.model.*
import aws.smithy.kotlin.runtime.collections.*
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.*
import aws.smithy.kotlin.runtime.http.request.*
import aws.smithy.kotlin.runtime.http.response.*
import aws.smithy.kotlin.runtime.operation.*
import kotlinx.coroutines.*
import java.io.*
import java.security.*
import java.time.*
import java.time.format.*
import java.util.*
import java.util.concurrent.atomic.*
import javax.xml.stream.*
import kotlin.coroutines.*
import kotlin.math.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalStdlibApi::class, ExperimentalTime::class)
class S3Fake(val region: String, val bucketNames: List<String>) {

    data class S3File(
        val key: String,
        val body: ByteArray,
        val storageClass: StorageClass,
        val lastModified: Instant,
        val metadata: Map<String, String>,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = key.hashCode()
        fun etag() = MessageDigest.getInstance("MD5").also { it.update(body) }.digest().toHexString()
    }

    private val buckets = bucketNames.associateWith { TreeMap<String, S3File>() }.toMutableMap()

    fun addFile(
        bucketName: String,
        key: String,
        body: ByteArray,
        storageClass: StorageClass = StorageClass.Standard,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val keyToUse = '/' + key.trimStart('/')
        buckets.getOrPut(bucketName) { TreeMap() }
            .put(keyToUse, S3File(keyToUse, body, storageClass, Clock.System.now(), metadata))
    }


    fun httpClientEngine(allowedAccessKeyIds: List<String>): CloseableHttpClientEngine {
        return object : CloseableHttpClientEngine {
            override val config: HttpClientEngineConfig get() = HttpClientEngineConfig.Default
            override val coroutineContext: CoroutineContext = SupervisorJob() + CoroutineName("http-client-engine-test-fake-context")
            private val closed = AtomicBoolean(false)

            override fun close() {
                if (!closed.compareAndSet(false, true)) return
                val job = coroutineContext[Job] as? CompletableJob ?: return
                job.complete()
            }

            override suspend fun roundTrip(
                context: ExecutionContext,
                request: HttpRequest,
            ): HttpCall {
                val host = request.headers["host"]!!
                assertEquals(request.url.host.toString(), host)
                assertTrue { bucketNames.any { host == "$it.s3.$region.amazonaws.com" } }
                assertEquals(443, request.url.port)
                val authorisation = request.headers["authorization"]!!
                assertTrue { authorisation.startsWith("AWS4-HMAC-SHA256 ") }
                assertTrue { allowedAccessKeyIds.any { authorisation.contains(it) } }

                val response = sleuthResponse(request)
                return HttpCall(
                    request,
                    response,
                )
            }

        }
    }

    private fun sleuthResponse(request: HttpRequest): HttpResponse {
        if (request.method != HttpMethod.GET)
            return HttpResponse(HttpStatusCode.MethodNotAllowed)

        val params = request.url.parameters.decodedParameters
        val bucketName = request.url.host.toString().substringBefore('.')

        if (request.url.path.segments.isEmpty() && params["list-type"]?.single() == "2")
            return listObjectsV2(bucketName, params)

        if (request.url.path.segments.isNotEmpty())
            return getObject(bucketName, request)

        return HttpResponse(HttpStatusCode.NotFound)
    }

    private fun getObject(bucketName: String, request: HttpRequest): HttpResponse {
        val bucket = buckets[bucketName]!!
        val file = bucket[request.url.path.decoded]
        if (file == null) {
            println("File ${request.url.path.decoded} not found in bucket $bucketName")
            return HttpResponse(HttpStatusCode.NotFound)
        }
        return HttpResponse(HttpStatusCode.OK, headers = Headers {
            set("ETag", file.etag())
            set("Content-Length", file.body.size.toString())
            set("Last-Modified", httpHeaderTimestamp(file.lastModified))
            set("x-amz-storage-class", file.storageClass.toString())
            file.metadata.forEach { (k, v) -> set("x-amz-meta-$k", v) }
        }, body = HttpBody.fromBytes(file.body))
    }

    private fun listObjectsV2(
        bucketName: String,
        params: MultiMap<String, String>,
    ): HttpResponse {
        val bucket = buckets[bucketName]!!
        val matchingFiles = bucket.values

        val maxFiles = 3

        // TODO: prefix
        // TODO: continuation

        val xmlOutputBytes = xmlDocument {
            element("ListBucketResult") {
                element("IsTruncated", (matchingFiles.size > maxFiles).toString())
                matchingFiles.forEach {
                    element("Contents") {
                        element("ChecksumAlgorithm", ChecksumAlgorithm.Crc32.value)
                        element("ChecksumType", ChecksumType.FullObject.value)
                        element("ETag", '"' + it.etag() + '"')
                        element("Key", it.key.trimStart('/'))
                        element("LastModified", it.lastModified.toJavaInstant().toString())
                        element("Size", it.body.size.toString())
                        element("StorageClass", it.storageClass.value)
                    }
                }
                element("Name", bucketName)
                element("Prefix", "")
                element("Delimiter", "/")
                element("MaxKeys", maxFiles.toString())
                // element("CommonPrefixes") {
                //     element("Prefix", "X")
                // }
                element("EncodingType", "url")
                element("KeyCount", min(maxFiles, matchingFiles.size).toString())
            }
        }

        return HttpResponse(HttpStatusCode.OK, body = HttpBody.fromBytes(xmlOutputBytes))
    }
}

@OptIn(ExperimentalTime::class)
private fun httpHeaderTimestamp(instant: Instant): String =
    DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.ofInstant(instant.toJavaInstant(), ZoneOffset.UTC))

val xmlFactory: XMLOutputFactory = XMLOutputFactory.newInstance()
private fun xmlDocument(children: XMLStreamWriter.() -> Unit): ByteArray {
    val bas = ByteArrayOutputStream()
    xmlFactory.createXMLStreamWriter(bas, "UTF-8").apply {
        writeStartDocument("UTF-8", "1.0")
        children()
        writeEndDocument()
        close()
    }
    return bas.toByteArray()
}

private fun XMLStreamWriter.element(name: String, children: XMLStreamWriter.() -> Unit) {
    writeStartElement(name)
    children()
    writeEndElement()
}

private fun XMLStreamWriter.element(name: String, contents: String) {
    writeStartElement(name)
    writeCharacters(contents)
    writeEndElement()
}
