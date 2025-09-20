package io.github.alexswilliams.photosync

import java.io.*

interface FileDecrypter {
    fun decryptPathOrNull(path: String): String?
    fun decryptFile(encryptedInput: InputStream, decryptedOutput: OutputStream)
}
