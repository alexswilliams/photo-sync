package io.github.alexswilliams.photosync

import org.bouncycastle.crypto.generators.*
import java.io.*
import java.security.*
import kotlin.io.encoding.*

private val RCLONE_DEFAULT_SALT = byteArrayOf(0xA8, 0x0D, 0xF4, 0x3A, 0x8F, 0xBD, 0x03, 0x08, 0xA7, 0xCA, 0xB8, 0x3E, 0x58, 0x1F, 0x86, 0xB1)
fun byteArrayOf(vararg elements: Int) = ByteArray(elements.size) { elements[it].toByte() }

interface FileDecrypter {
    fun decryptPathOrNull(path: String): String?

    fun decryptFile(encryptedInput: InputStream, decryptedOutput: OutputStream) {
        TODO()
    }
}

class RCryptDecrypter(password: String) : FileDecrypter {
    private val dataKeyMaterial: ByteArray
    private val nameCipher: Eme.EmeCipher

    init {
        Security.setProperty("crypto.policy", "unlimited")
        val key = SCrypt.generate(password.toByteArray(), RCLONE_DEFAULT_SALT, 16384, 8, 1, 32 + 32 + 16)
        dataKeyMaterial = key.sliceArray(0 until 32)
        nameCipher = Eme.EmeCipher(
            key = key.sliceArray(32 until 64),
            tweak = key.sliceArray(64 until 80)
        )
    }

    fun decryptNamePart(unpaddedCipherTextBase64: String): String {
        val cipherText = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .decode(unpaddedCipherTextBase64)
        val paddedPlainText = Eme.emeDecrypt(nameCipher, cipherText)
        return pkcs7UnPad(paddedPlainText)
            .decodeToString()
    }

    override fun decryptPathOrNull(path: String): String? = try {
        path.split('/')
            .joinToString(File.separator) { if (it.isEmpty()) "" else decryptNamePart(it) }
    } catch (_: Exception) {
        null
    }

    private companion object {
        private fun pkcs7UnPad(paddedInput: ByteArray): ByteArray {
            val padLength = paddedInput.last().toInt()
            if (paddedInput.size < padLength) throw IllegalArgumentException("Input is too short to contain declared amount of padding")
            val padding = paddedInput.slice((paddedInput.size - padLength) until paddedInput.size)
            if (padding.size != padLength) throw AssertionError("Padding index calculation is incorrect")
            if (padding.any { it.toInt() != padLength }) throw IllegalArgumentException("Padding is not valid, should all be the same byte value")
            return paddedInput.sliceArray(0 until (paddedInput.size - padLength))
        }
    }
}
