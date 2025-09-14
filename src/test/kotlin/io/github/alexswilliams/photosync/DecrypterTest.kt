package io.github.alexswilliams.photosync

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.*

private const val testPassword = "kjIjoZRjnxwUN2xFTWkswdEDw3msGoPN3KVDD3LWDd8"

class DecrypterTest {
    @TestFactory
    fun knownExamples(): Collection<DynamicTest> =
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
                val decrypter = Decrypter(testPassword)
                assertThat(decrypter.decryptNamePart(cipher)).isEqualTo(plain)
            }
        }
}
