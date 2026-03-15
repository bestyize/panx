package xyz.thewind.panx.repository

import xyz.thewind.panx.domain.DownloadTask
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface DownloadTaskRepository : CrudRepository<DownloadTask, Long> {

    @Query(
        """
        select * from download_tasks
        where owner_id = :ownerId
        order by created_at desc
        """,
    )
    fun findByOwnerId(ownerId: Long): List<DownloadTask>
}
