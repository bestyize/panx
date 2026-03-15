package xyz.thewind.panx.repository

import xyz.thewind.panx.domain.ShareAccessLog
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface ShareAccessLogRepository : CrudRepository<ShareAccessLog, Long> {

    @Query(
        """
        select count(*) from share_access_logs
        where share_link_id = :shareLinkId
        """,
    )
    fun countByShareLinkId(shareLinkId: Long): Long

    @Modifying
    @Query(
        """
        delete from share_access_logs
        where share_link_id = :shareLinkId
        """,
    )
    fun deleteByShareLinkId(shareLinkId: Long)
}
