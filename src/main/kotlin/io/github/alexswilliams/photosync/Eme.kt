package io.github.alexswilliams.photosync

import javax.crypto.*
import javax.crypto.spec.*
import kotlin.experimental.*

object Eme {
    class EmeCipher(key: ByteArray, val tweak: ByteArray) {
        private val aesEncrypt = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        private val aesDecrypt = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES")) }
        val blockSize = aesEncrypt.blockSize

        init {
            if (aesEncrypt.blockSize != aesDecrypt.blockSize) throw Exception("Forward and reverse ciphers must have same block size")
            if (blockSize != 16) throw Exception("TODO: block size must be 16 bytes")
            if (tweak.size != blockSize) throw Exception("Tweak size must be equal to the cipher block size")
        }

        fun encrypt(input: ByteArray): ByteArray = aesEncrypt.doFinal(input)
        fun decrypt(input: ByteArray): ByteArray = aesDecrypt.doFinal(input)
    }

    private fun ByteArray.block(j: Int, blockSize: Int): ByteArray = sliceArray(j * blockSize until (j * blockSize + blockSize))
    private infix fun ByteArray.xor(other: ByteArray): ByteArray = ByteArray(this.size) { this[it] xor other[it] }

    // https://eprint.iacr.org/2003/147.pdf
    @Suppress("LocalVariableName")
    fun emeDecrypt(cipher: EmeCipher, cipherText: ByteArray): ByteArray {
        with(cipher) {
            if (cipherText.size % blockSize != 0) throw Exception("Invalid cipherText length, expected multiple of %d bytes".format(blockSize))
            val blockCount = cipherText.size / blockSize
            if (blockCount == 0) throw Exception("EME not supported for empty messages")
            if (blockCount > blockSize * 8) throw Exception("EME becomes too weak when there are more blocks than bits in a cipher block")
            val nullBlock = ByteArray(blockSize)

            var lastL = encrypt(nullBlock)
            val L = Array(blockCount) { lastL.shift().also { lastL = it } }

            val CCC = Array(blockCount) { i -> decrypt(L[i] xor cipherText.block(i, blockSize)) }

            val SC = CCC.reduce { a, b -> a xor b }
            val MC = SC xor tweak
            val MP = decrypt(MC)
            val M = MC xor MP

            var lastM = M
            val PPP = Array(blockCount) { i -> (CCC[i] xor lastM).also { lastM = lastM.shift() } }
            val SP = PPP.reduce { a, b -> a xor b } xor PPP[0]
            PPP[0] = MP xor SP xor tweak

            val P = Array(blockCount) { i -> decrypt(PPP[i]) xor L[i] }

            val plainText = ByteArray(blockCount * blockSize)
            P.forEachIndexed { index, bytes -> bytes.copyInto(plainText, index * blockSize) }
            return plainText
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun ByteArray.shift(): ByteArray {
        if (size != 16) throw Exception("The EME paper only defined how to rotate 128-bit blocks")
        val input = this.asUByteArray()
        val output = UByteArray(size)
        output[0] = (2u * input[0]).toUByte()
        for (j in 1 until size) {
            output[j] = (2u * input[j]).toUByte()
            if (input[j - 1] >= 0x80u) output[j] = (output[j] + 1u).toUByte()
        }
        if (input[lastIndex] >= 0x80u) output[0] = output[0] xor 0x87u
        return output.asByteArray()
    }
}
