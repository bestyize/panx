package xyz.thewind.panx.service

import xyz.thewind.panx.domain.DownloadTask
import xyz.thewind.panx.domain.DownloadTaskStatus
import xyz.thewind.panx.domain.UserAccount
import xyz.thewind.panx.dto.CreateRemoteDownloadTaskRequest
import xyz.thewind.panx.dto.DownloadTaskResponse
import xyz.thewind.panx.exception.BadRequestException
import xyz.thewind.panx.exception.ForbiddenException
import xyz.thewind.panx.exception.NotFoundException
import xyz.thewind.panx.repository.DownloadTaskRepository
import xyz.thewind.panx.support.FileSizeFormatter
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URLConnection
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Service
class DownloadTaskService(
    private val downloadTaskRepository: DownloadTaskRepository,
    private val fileService: FileService,
) {

    private val executor = Executors.newCachedThreadPool()
    private val runningTasks = ConcurrentHashMap<Long, Future<*>>()
    private val cancelFlags = ConcurrentHashMap<Long, Boolean>()

    fun listTasks(owner: UserAccount): List<DownloadTaskResponse> = downloadTaskRepository.findByOwnerId(owner.id!!)
        .map(::toResponse)

    @Transactional
    fun createTask(owner: UserAccount, request: CreateRemoteDownloadTaskRequest): DownloadTaskResponse {
        val sourceUri = runCatching { URI.create(request.sourceUrl.trim()) }.getOrNull()
            ?: throw BadRequestException("Invalid source URL")
        if (sourceUri.scheme !in setOf("http", "https")) {
            throw BadRequestException("Only HTTP and HTTPS downloads are supported")
        }
        request.parentId?.let { fileService.requireFolder(owner.id!!, it) }

        val fileName = request.fileName?.trim().takeUnless { it.isNullOrBlank() }
            ?: sourceUri.path.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "remote-download-${System.currentTimeMillis()}.bin"

        val task = downloadTaskRepository.save(
            DownloadTask(
                ownerId = owner.id!!,
                targetParentId = request.parentId,
                sourceUrl = request.sourceUrl.trim(),
                fileName = fileName,
                status = DownloadTaskStatus.PENDING,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
        schedule(owner, task.id!!)
        return toResponse(task)
    }

    @Transactional
    fun cancelTask(owner: UserAccount, id: Long) {
        val task = findOwnedTask(owner.id!!, id)
        if (task.status in setOf(DownloadTaskStatus.COMPLETED, DownloadTaskStatus.FAILED, DownloadTaskStatus.CANCELLED)) {
            throw BadRequestException("Task is already finished")
        }
        cancelFlags[id] = true
        runningTasks.remove(id)?.cancel(true)
        downloadTaskRepository.save(
            task.copy(
                status = DownloadTaskStatus.CANCELLED,
                updatedAt = Instant.now(),
                errorMessage = "Cancelled by user",
            ),
        )
    }

    private fun schedule(owner: UserAccount, taskId: Long) {
        val future = CompletableFuture.runAsync({
            execute(owner, taskId)
        }, executor)
        runningTasks[taskId] = future
    }

    private fun execute(owner: UserAccount, taskId: Long) {
        val started = updateStatus(taskId, DownloadTaskStatus.RUNNING)
        val task = started ?: return
        var createdFileNodeId: Long? = null
        try {
            val connection = URI.create(task.sourceUrl).toURL().openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 60_000
            }
            val totalBytes = connection.contentLengthLong.takeIf { it >= 0 }
            persistTask(
                task.copy(
                    status = DownloadTaskStatus.RUNNING,
                    totalBytes = totalBytes,
                    updatedAt = Instant.now(),
                ),
            )

            connection.getInputStream().use { input ->
                val fileNode = fileService.storeStreamedFile(
                    owner = owner,
                    parentId = task.targetParentId,
                    fileName = task.fileName,
                    contentType = URLConnection.guessContentTypeFromName(task.fileName)
                        ?: connection.contentType
                        ?: "application/octet-stream",
                    inputStream = input,
                    shouldContinue = { !cancelFlags.getOrDefault(taskId, false) && !Thread.currentThread().isInterrupted },
                    onProgress = { downloaded ->
                        val current = downloadTaskRepository.findById(taskId).orElse(null) ?: return@storeStreamedFile
                        persistTask(
                            current.copy(
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes,
                                updatedAt = Instant.now(),
                            ),
                        )
                    },
                )
                createdFileNodeId = fileNode.id
                val completedTask = downloadTaskRepository.findById(taskId).orElse(null) ?: return
                persistTask(
                    completedTask.copy(
                        fileNodeId = fileNode.id,
                        status = DownloadTaskStatus.COMPLETED,
                        bytesDownloaded = fileNode.size,
                        totalBytes = totalBytes ?: fileNode.size,
                        updatedAt = Instant.now(),
                        errorMessage = null,
                    ),
                )
            }
        } catch (ex: InterruptedException) {
            handleCancellation(owner, taskId, createdFileNodeId)
        } catch (ex: Exception) {
            if (cancelFlags.getOrDefault(taskId, false)) {
                handleCancellation(owner, taskId, createdFileNodeId)
            } else {
                createdFileNodeId?.let { fileService.deleteNodeByOwnerId(owner.id!!, it, recursive = false) }
                val current = downloadTaskRepository.findById(taskId).orElse(null)
                if (current != null) {
                    persistTask(
                        current.copy(
                            status = DownloadTaskStatus.FAILED,
                            updatedAt = Instant.now(),
                            errorMessage = ex.message?.take(1000) ?: "Remote download failed",
                        ),
                    )
                }
            }
        } finally {
            runningTasks.remove(taskId)
            cancelFlags.remove(taskId)
        }
    }

    private fun handleCancellation(owner: UserAccount, taskId: Long, fileNodeId: Long?) {
        fileNodeId?.let { fileService.deleteNodeByOwnerId(owner.id!!, it, recursive = false) }
        val current = downloadTaskRepository.findById(taskId).orElse(null) ?: return
        persistTask(
            current.copy(
                status = DownloadTaskStatus.CANCELLED,
                updatedAt = Instant.now(),
                errorMessage = "Cancelled by user",
            ),
        )
    }

    private fun findOwnedTask(ownerId: Long, id: Long): DownloadTask {
        val task = downloadTaskRepository.findById(id).orElseThrow { NotFoundException("Download task not found") }
        if (task.ownerId != ownerId) {
            throw ForbiddenException("Cannot access another user's download task")
        }
        return task
    }

    private fun updateStatus(taskId: Long, status: DownloadTaskStatus): DownloadTask? {
        val task = downloadTaskRepository.findById(taskId).orElse(null) ?: return null
        return persistTask(task.copy(status = status, updatedAt = Instant.now()))
    }

    private fun persistTask(task: DownloadTask): DownloadTask = downloadTaskRepository.save(task)

    private fun toResponse(task: DownloadTask): DownloadTaskResponse = DownloadTaskResponse(
        id = task.id!!,
        targetParentId = task.targetParentId,
        fileNodeId = task.fileNodeId,
        sourceUrl = task.sourceUrl,
        fileName = task.fileName,
        status = task.status,
        bytesDownloaded = task.bytesDownloaded,
        bytesDownloadedDisplay = FileSizeFormatter.format(task.bytesDownloaded),
        totalBytes = task.totalBytes,
        totalBytesDisplay = task.totalBytes?.let(FileSizeFormatter::format),
        errorMessage = task.errorMessage,
        createdAt = task.createdAt,
        updatedAt = task.updatedAt,
    )

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}
