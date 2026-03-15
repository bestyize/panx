package xyz.thewind.panx.service

import org.springframework.core.io.Resource
import java.io.InputStream

interface StorageService {
    fun store(ownerId: Long, fileNodeId: Long, originalFilename: String?, inputStream: InputStream): StoredFile

    fun storeStreaming(
        ownerId: Long,
        fileNodeId: Long,
        originalFilename: String?,
        inputStream: InputStream,
        shouldContinue: () -> Boolean = { true },
        onProgress: (Long) -> Unit = {},
    ): StoredFile

    fun loadAsResource(relativePath: String): Resource

    fun delete(relativePath: String?)

    fun readTextPreview(relativePath: String, maxBytes: Int): String
}

data class StoredFile(
    val relativePath: String,
    val size: Long,
)
