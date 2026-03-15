package xyz.thewind.panx

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import xyz.thewind.panx.repository.ShareAccessLogRepository
import java.net.InetSocketAddress
import java.util.concurrent.Executors

@SpringBootTest
@AutoConfigureMockMvc
class FileAndShareIntegrationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var shareAccessLogRepository: ShareAccessLogRepository

    @Test
    fun `folder upload download and share flow works`() {
        val suffix = System.currentTimeMillis()
        val folderResponse = mockMvc.post("/api/files/folders") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"docs-$suffix"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("docs-$suffix") }
        }.andReturn().response.contentAsString

        val folderId = readTree(folderResponse).path("data").path("id").asLong()

        val targetFolderResponse = mockMvc.post("/api/files/folders") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"archive-$suffix"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("archive-$suffix") }
        }.andReturn().response.contentAsString

        val targetFolderId = readTree(targetFolderResponse).path("data").path("id").asLong()

        val multipartFile = MockMultipartFile("files", "hello-$suffix.txt", MediaType.TEXT_PLAIN_VALUE, "hello panx".toByteArray())
        val uploadResponse = mockMvc.multipart("/api/files/upload") {
            file(multipartFile)
            param("parentId", folderId.toString())
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].name") { value("hello-$suffix.txt") }
        }.andReturn().response.contentAsString

        val fileId = readTree(uploadResponse).path("data").path(0).path("id").asLong()

        mockMvc.get("/api/files/tree") {
            with(user("admin").roles("USER"))
            param("excludeNodeId", fileId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].path") { value("/") }
        }

        mockMvc.patch("/api/files/$fileId/move") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetParentId":$targetFolderId}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.parentId") { value(targetFolderId) }
        }

        mockMvc.get("/api/files") {
            with(user("admin").roles("USER"))
            param("parentId", targetFolderId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.items[0].id") { value(fileId) }
        }

        mockMvc.get("/api/files/$fileId/download") {
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
            content { bytes("hello panx".toByteArray()) }
        }

        val shareResponse = mockMvc.post("/api/shares") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"fileNodeId":$fileId}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.fileNodeId") { value(fileId) }
        }.andReturn().response.contentAsString

        val shareId = readTree(shareResponse).path("data").path("id").asLong()
        val shareToken = readTree(shareResponse).path("data").path("shareToken").asText()

        mockMvc.get("/share/$shareToken").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("hello-$suffix.txt")) }
        }

        mockMvc.get("/api/shares/$shareToken/download").andExpect {
            status { isOk() }
            content { bytes("hello panx".toByteArray()) }
        }

        kotlin.test.assertEquals(2, shareAccessLogRepository.countByShareLinkId(shareId))

        mockMvc.delete("/api/shares/$shareId") {
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
        }

        mockMvc.get("/api/shares/$shareToken").andExpect {
            status { isNotFound() }
        }

        mockMvc.delete("/api/files/$folderId") {
            with(user("admin").roles("USER"))
            param("recursive", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }

        mockMvc.delete("/api/files/$targetFolderId") {
            with(user("admin").roles("USER"))
            param("recursive", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }

    @Test
    fun `folder share supports browsing and child file download`() {
        val suffix = System.currentTimeMillis()
        val folderResponse = mockMvc.post("/api/files/folders") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"shared-folder-$suffix"}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val folderId = readTree(folderResponse).path("data").path("id").asLong()
        val nestedResponse = mockMvc.post("/api/files/folders") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"parentId":$folderId,"name":"nested-$suffix"}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val nestedFolderId = readTree(nestedResponse).path("data").path("id").asLong()

        val multipartFile = MockMultipartFile("files", "note-$suffix.txt", MediaType.TEXT_PLAIN_VALUE, "shared folder content".toByteArray())
        val uploadResponse = mockMvc.multipart("/api/files/upload") {
            file(multipartFile)
            param("parentId", nestedFolderId.toString())
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString

        val fileId = readTree(uploadResponse).path("data").path(0).path("id").asLong()

        val shareResponse = mockMvc.post("/api/shares") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"fileNodeId":$folderId}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.nodeType") { value("FOLDER") }
        }.andReturn().response.contentAsString

        val shareToken = readTree(shareResponse).path("data").path("shareToken").asText()
        val shareId = readTree(shareResponse).path("data").path("id").asLong()

        mockMvc.get("/share/$shareToken").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("shared-folder-$suffix")) }
            content { string(org.hamcrest.Matchers.containsString("nested-$suffix")) }
        }

        mockMvc.get("/share/$shareToken") {
            param("parentId", nestedFolderId.toString())
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("note-$suffix.txt")) }
        }

        mockMvc.get("/api/shares/$shareToken/download") {
            param("nodeId", fileId.toString())
        }.andExpect {
            status { isOk() }
            content { bytes("shared folder content".toByteArray()) }
        }

        mockMvc.get("/api/shares/$shareToken/preview") {
            param("nodeId", fileId.toString())
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("shared folder content")) }
        }

        kotlin.test.assertEquals(4, shareAccessLogRepository.countByShareLinkId(shareId))
    }

    @Test
    fun `text file creation and remote download task flow works`() {
        val suffix = System.currentTimeMillis()
        val textResponse = mockMvc.post("/api/files/text") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"draft-$suffix.txt","content":"hello text file"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("draft-$suffix.txt") }
        }.andReturn().response.contentAsString

        val textFileId = readTree(textResponse).path("data").path("id").asLong()

        mockMvc.get("/api/files/$textFileId/preview") {
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("hello text file")) }
        }

        val remoteContent = "remote payload $suffix".toByteArray()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/remote.txt") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, remoteContent.size.toLong())
            exchange.responseBody.use { output -> output.write(remoteContent) }
        }
        server.createContext("/slow.bin") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, 512L * 1024)
            exchange.responseBody.use { output ->
                repeat(64) {
                    output.write(ByteArray(8 * 1024) { 1 })
                    output.flush()
                    Thread.sleep(40)
                }
            }
        }
        server.start()
        try {
            val sourceUrl = "http://127.0.0.1:${server.address.port}/remote.txt"
            val taskResponse = mockMvc.post("/api/files/download-tasks") {
                with(user("admin").roles("USER"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"sourceUrl":"$sourceUrl","fileName":"remote-$suffix.txt"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.fileName") { value("remote-$suffix.txt") }
            }.andReturn().response.contentAsString

            val taskId = readTree(taskResponse).path("data").path("id").asLong()
            var completedNodeId = -1L
            repeat(20) {
                Thread.sleep(150)
                val listResponse = mockMvc.get("/api/files/download-tasks") {
                    with(user("admin").roles("USER"))
                }.andExpect {
                    status { isOk() }
                }.andReturn().response.contentAsString

                val matched = readTree(listResponse).path("data").firstOrNull { it.path("id").asLong() == taskId }
                if (matched != null && matched.path("status").asText() == "COMPLETED") {
                    completedNodeId = matched.path("fileNodeId").asLong()
                    return@repeat
                }
            }

            kotlin.test.assertTrue(completedNodeId > 0, "remote download task should complete")

            mockMvc.get("/api/files/$completedNodeId/download") {
                with(user("admin").roles("USER"))
            }.andExpect {
                status { isOk() }
                content { bytes(remoteContent) }
            }

            val slowTaskResponse = mockMvc.post("/api/files/download-tasks") {
                with(user("admin").roles("USER"))
                contentType = MediaType.APPLICATION_JSON
                content = """{"sourceUrl":"http://127.0.0.1:${server.address.port}/slow.bin","fileName":"slow-$suffix.bin"}"""
            }.andExpect {
                status { isOk() }
            }.andReturn().response.contentAsString

            val slowTaskId = readTree(slowTaskResponse).path("data").path("id").asLong()
            mockMvc.delete("/api/files/download-tasks/$slowTaskId") {
                with(user("admin").roles("USER"))
            }.andExpect {
                status { isOk() }
            }

            var cancelled = false
            repeat(20) {
                Thread.sleep(100)
                val listResponse = mockMvc.get("/api/files/download-tasks") {
                    with(user("admin").roles("USER"))
                }.andReturn().response.contentAsString
                val matched = readTree(listResponse).path("data").firstOrNull { it.path("id").asLong() == slowTaskId }
                if (matched != null && matched.path("status").asText() == "CANCELLED") {
                    cancelled = true
                    return@repeat
                }
            }
            kotlin.test.assertTrue(cancelled, "slow task should be cancelled")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `video player page and ranged preview work`() {
        val suffix = System.currentTimeMillis()
        val multipartFile = MockMultipartFile(
            "files",
            "clip-$suffix.mp4",
            "video/mp4",
            "fake-video-content".toByteArray(),
        )
        val uploadResponse = mockMvc.multipart("/api/files/upload") {
            file(multipartFile)
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].iconKey") { value("video") }
        }.andReturn().response.contentAsString

        val fileId = readTree(uploadResponse).path("data").path(0).path("id").asLong()

        mockMvc.get("/app/files/$fileId/player") {
            with(user("admin").roles("USER"))
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("clip-$suffix.mp4")) }
            content { string(org.hamcrest.Matchers.containsString("/api/files/$fileId/preview")) }
        }

        mockMvc.get("/api/files/$fileId/preview") {
            with(user("admin").roles("USER"))
            header("Range", "bytes=0-3")
        }.andExpect {
            status { isPartialContent() }
        }

        val shareResponse = mockMvc.post("/api/shares") {
            with(user("admin").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"fileNodeId":$fileId}"""
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val shareToken = readTree(shareResponse).path("data").path("shareToken").asText()

        mockMvc.get("/share/$shareToken/player").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("/api/shares/$shareToken/preview")) }
        }

        mockMvc.get("/api/shares/$shareToken/preview") {
            header("Range", "bytes=0-3")
        }.andExpect {
            status { isPartialContent() }
        }
    }

    private fun readTree(content: String): JsonNode = objectMapper.readTree(content)
}
