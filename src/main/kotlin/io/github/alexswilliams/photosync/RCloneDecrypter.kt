package io.github.alexswilliams.photosync

import org.bouncycastle.crypto.engines.*
import org.bouncycastle.crypto.generators.*
import org.bouncycastle.crypto.macs.*
import org.bouncycastle.crypto.params.*
import java.io.*
import java.nio.*
import java.nio.charset.CodingErrorAction.*
import java.security.*
import kotlin.io.encoding.*

class RCloneDecrypter(password: String) : FileDecrypter {
    private val dataKeyMaterial: ByteArray
    private val nameCipher: Eme.EmeCipher

    init {
        Security.setProperty("crypto.policy", "unlimited")
        val key = SCrypt.generate(password.toByteArray(), DEFAULT_SALT, 16384, 8, 1, 32 + 32 + 16)
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
        val unpadded = pkcs7UnPad(paddedPlainText)
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(REPORT)
            .onUnmappableCharacter(REPORT)
            .decode(ByteBuffer.wrap(unpadded)).toString()
            .also { path -> if (path.any { it.isISOControl() }) throw Exception("Path contained control character") }
    }

    override fun decryptPathOrNull(path: String): String? = try {
        path.split('/')
            .joinToString(File.separator) { if (it.isEmpty()) "" else decryptNamePart(it) }
    } catch (_: Exception) {
        null
    }

    override fun decryptFile(encryptedInput: InputStream, decryptedOutput: OutputStream) {
        if (!encryptedInput.readNBytes(8).contentEquals(MAGIC))
            throw IllegalArgumentException("Input is not an rclone-encrypted file")
        val nonce = encryptedInput.readNBytes(24)
        if (nonce.size != 24)
            throw IllegalArgumentException("Could not read nonce from file")

        fun ByteArray.inc() {
            for (i in 0 until size) {
                val digit = this[i]
                this[i] = ((digit + 1) and 0xff).toByte()
                if (this[i] != 0.toByte()) break
            }
        }

        fun decryptBlock(nonce: ByteArray, key: ByteArray, blockPlusMac: ByteArray): ByteArray {
            val salsa = XSalsa20Engine().apply { init(false, ParametersWithIV(KeyParameter(key), nonce)) }

            val mac = blockPlusMac.sliceArray(0 until 16)
            if (mac.size != 16) throw Exception("Expected Poly1305 MAC to be 16 bytes")
            val block = blockPlusMac.sliceArray(16 until blockPlusMac.size)
            if (block.isEmpty()) throw Exception("Expected block to contain data")

            val macCalculated = with(Poly1305()) {
                val macKey = ByteArray(32)
                salsa.processBytes(macKey, 0, macKey.size, macKey, 0)
                init(KeyParameter(macKey))
                update(block, 0, block.size)
                ByteArray(macSize).also { doFinal(it, 0) }
            }
            if (!mac.contentEquals(macCalculated)) throw Exception("MAC validation failed")

            // BC seems to factor in the last 32 byes of keystream generated above (it should generate 64 byte chunks) when it decrypts the ciphertext?  Perhaps they're cached internally
            return ByteArray(block.size).also { salsa.processBytes(block, 0, block.size, it, 0) }
        }

        var block = encryptedInput.readNBytes(16 + 64 * 1024)
        while (block.isNotEmpty()) {
            val decrypted = decryptBlock(nonce, dataKeyMaterial, block)
            decryptedOutput.write(decrypted)
            nonce.inc()
            block = encryptedInput.readNBytes(16 + 64 * 1024)
        }
    }

    private companion object {
        private val MAGIC = "RCLONE".toByteArray() + byteArrayOf(0, 0)

        // https://github.com/rclone/rclone/blob/231083647ef96fc97fd4dff6b88b93450a677693/backend/crypt/cipher.go#L59
        private val DEFAULT_SALT = byteArrayOf(0xA8, 0x0D, 0xF4, 0x3A, 0x8F, 0xBD, 0x03, 0x08, 0xA7, 0xCA, 0xB8, 0x3E, 0x58, 0x1F, 0x86, 0xB1)
        private fun byteArrayOf(vararg elements: Int) = ByteArray(elements.size) { elements[it].toByte() }

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
