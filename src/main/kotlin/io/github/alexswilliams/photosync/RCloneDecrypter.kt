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

typealias Nonce = ByteArray

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
        val nonceFromFile: Nonce = encryptedInput.readNBytes(24)
        if (nonceFromFile.size != 24)
            throw IllegalArgumentException("Could not read nonce from file")

        fun Nonce.carry(start: Int) {
            for (i in start until size) {
                val digit = this[i]
                val newDigit = ((digit + 1) and 0xff).toByte()
                this[i] = newDigit
                if (newDigit >= digit) break
            }
        }

        fun Nonce.inc() = carry(0)
        fun Nonce.add(x: Long) {
            var x = x
            var carry: Int = 0
            for (i in 0 until 8) {
                val digit = this[i]
                val xDigit = (x and 0xff).toByte()
                x = x shl 8
                carry = (carry + digit + xDigit) and 0xffff
                this[i] = (carry and 0xff).toByte()
                carry = carry shl 8
            }
            if (carry != 0) carry(8)
        }

        val polyMac = Poly1305()
        val mac = encryptedInput.readNBytes(polyMac.macSize)
        if (mac.size != polyMac.macSize) throw Exception("Expected block to contain data")
        val salsa = XSalsa20Engine().apply {
            init(false, ParametersWithIV(KeyParameter(dataKeyMaterial), nonceFromFile))
        }
        val macKey = ByteArray(32)
        salsa.processBytes(macKey, 0, macKey.size, macKey, 0) // TODO: what?
        polyMac.init(KeyParameter(macKey))
        val block = encryptedInput.readNBytes(16 * 1024)
        polyMac.update(block, 0, block.size)
        val macCalculated = ByteArray(polyMac.macSize)
        polyMac.doFinal(macCalculated, 0)
        if (mac.contentEquals(macCalculated)) {
            val decrypted = ByteArray(block.size)
            salsa.processBytes(block, 0, block.size, decrypted, 0)
            println(decrypted.toHexString())
            println(decrypted.decodeToString())
            decryptedOutput.write(decrypted)
        } else throw Exception("MAC validation failed")

        TODO("Not yet implemented")
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
