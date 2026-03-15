package xyz.thewind.panx.service

import xyz.thewind.panx.config.PanxStorageProperties
import xyz.thewind.panx.exception.BadRequestException
import xyz.thewind.panx.exception.NotFoundException
import xyz.thewind.panx.support.FilenameSanitizer
import jakarta.annotation.PostConstruct
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class LocalStorageService(
    private val storageProperties: PanxStorageProperties,
) : StorageService {

    private val rootDir: Path = storageProperties.rootDir.toAbsolutePath().normalize()

    @PostConstruct
    fun init() {
        Files.createDirectories(rootDir)
    }

    override fun store(ownerId: Long, fileNodeId: Long, originalFilename: String?, inputStream: InputStream): StoredFile {
        return storeStreaming(ownerId, fileNodeId, originalFilename, inputStream)
    }

    override fun storeStreaming(
        ownerId: Long,
        fileNodeId: Long,
        originalFilename: String?,
        inputStream: InputStream,
        shouldContinue: () -> Boolean,
        onProgress: (Long) -> Unit,
    ): StoredFile {
        val extension = FilenameSanitizer.sanitize(originalFilename ?: "")
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            ?: ""
        val relativePath = Path.of("owner-$ownerId", "node-$fileNodeId$extension").toString()
        val target = resolve(relativePath)
        Files.createDirectories(target.parent)
        inputStream.use { source ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    if (!shouldContinue()) {
                        throw InterruptedException("Transfer cancelled")
                    }
                    val read = source.read(buffer)
                    if (read < 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    total += read
                    onProgress(total)
                }
            }
        }
        return StoredFile(relativePath = relativePath, size = Files.size(target))
    }

    override fun loadAsResource(relativePath: String): Resource {
        val file = resolve(relativePath)
        if (!Files.exists(file) || !Files.isReadable(file)) {
            throw NotFoundException("Stored file not found")
        }
        return UrlResource(file.toUri())
    }

    override fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) {
            return
        }
        Files.deleteIfExists(resolve(relativePath))
    }

    override fun readTextPreview(relativePath: String, maxBytes: Int): String {
        val path = resolve(relativePath)
        if (!Files.exists(path)) {
            throw NotFoundException("Stored file not found")
        }
        Files.newInputStream(path).use { input ->
            val bytes = input.readNBytes(maxBytes)
            return String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun resolve(relativePath: String): Path {
        val path = rootDir.resolve(relativePath).normalize()
        if (!path.startsWith(rootDir)) {
            throw BadRequestException("Invalid storage path")
        }
        return path
    }
}
