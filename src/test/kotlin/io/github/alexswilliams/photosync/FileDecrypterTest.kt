package io.github.alexswilliams.photosync

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*
import java.io.*

private const val testPassword = "kjIjoZRjnxwUN2xFTWkswdEDw3msGoPN3KVDD3LWDd8"

class FileDecrypterTest {

    @TestFactory
    fun `known examples`(): Collection<DynamicTest> =
        mapOf(
            "test" to "oHw4y5oZcRpis2VveUx3yg",
            "test2" to "Ta4vnIra9RpUc3zSa9LDfw",
            "exactly15ABCDEF" to "CRQ8MmpMaKDKEBujWexJ0w",
            "exactly16ABCDEFG" to "pEvnd_ejhDhAmSnc4r6oI9oJL8uPIkbmRvNIyaxclvg",
            "an example with spaces" to "IElRmNGiAEmpTVDkLRfk8GKTLSZfDSEawkLw8_8jl8s",
            "exactly31ABCDEFGHIJKLMNOPQRSTUV" to "PG9-KvziPynvRMwwfuREXEnmMUcRoGZVONEQgyvHezU",
            "exactly32ABCDEFGHIJKLMNOPQRSTUVW" to "MFqVXYoZFwyCtUbPXSjRXI0dMZI4zaG5PiqFsx7P9SEz2s2kteLtRdzN1XC1rsYS",
        ).map { (plain, cipher) ->
            DynamicTest.dynamicTest(plain) {
                val filenameDecrypter = RCloneDecrypter(testPassword)
                assertThat(filenameDecrypter.decryptNamePart(cipher)).isEqualTo(plain)
            }
        }

    @TestFactory
    fun `path decryption`(): Collection<DynamicTest> =
        mapOf(
            "" to "",
            "/" to "/",
            "test" to "oHw4y5oZcRpis2VveUx3yg",
            "/test" to "/oHw4y5oZcRpis2VveUx3yg",
            "test/" to "oHw4y5oZcRpis2VveUx3yg/",
            "/test/" to "/oHw4y5oZcRpis2VveUx3yg/",
            "test/test2" to "oHw4y5oZcRpis2VveUx3yg/Ta4vnIra9RpUc3zSa9LDfw",
            "/test/test2" to "/oHw4y5oZcRpis2VveUx3yg/Ta4vnIra9RpUc3zSa9LDfw",
            "/test/test2/" to "/oHw4y5oZcRpis2VveUx3yg/Ta4vnIra9RpUc3zSa9LDfw/",
            "test/test2/" to "oHw4y5oZcRpis2VveUx3yg/Ta4vnIra9RpUc3zSa9LDfw/",
        ).map { (plain, cipher) ->
            DynamicTest.dynamicTest("Path: $plain") {
                val fileDecrypter: FileDecrypter = RCloneDecrypter(testPassword)
                assertThat(fileDecrypter.decryptPathOrNull(cipher))
                    .isEqualTo(plain)
            }
        }

    @Test
    fun `paths not decryptable with this key return null`() {
        val fileDecrypter: FileDecrypter = RCloneDecrypter(testPassword)
        assertThat(fileDecrypter.decryptPathOrNull("some-path"))
            .isNull()
    }

    @Test
    fun `decrypts small files (under 64KiB)`() {
        val fileDecrypter: FileDecrypter = RCloneDecrypter(testPassword)
        val input = this::class.java.getResourceAsStream("Ta4vnIra9RpUc3zSa9LDfw")!!
        val byteArrayStream = ByteArrayOutputStream()

        fileDecrypter.decryptFile(input, byteArrayStream)

        assertThat(byteArrayStream.toByteArray()).asString().isEqualTo("Some encrypted test file")
    }

    @Test
    fun `decrypts multi-block files (over 64KiB)`() {
        val input = this::class.java.getResourceAsStream("AILEGR-HuyfFkAvD_LxzTA")!!
        val byteArrayStream = ByteArrayOutputStream()

        val fileDecrypter: FileDecrypter = RCloneDecrypter(testPassword)
        fileDecrypter.decryptFile(input, byteArrayStream)

        // https://de.wikipedia.org/wiki/Datei:Young_Maine_Coon_Male.jpg
        assertThat(byteArrayStream.toByteArray()).isEqualTo(this::class.java.getResource("kitten.jpg")!!.readBytes())
    }
}
