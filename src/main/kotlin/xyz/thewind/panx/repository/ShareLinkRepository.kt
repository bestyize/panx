package xyz.thewind.panx.repository

import xyz.thewind.panx.domain.ShareLink
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface ShareLinkRepository : CrudRepository<ShareLink, Long> {

    fun findByShareToken(shareToken: String): ShareLink?

    @Query(
        """
        select * from share_links
        where file_node_id = :fileNodeId
        order by created_at desc
        """,
    )
    fun findByFileNodeId(fileNodeId: Long): List<ShareLink>

    @Query(
        """
        select * from share_links
        where created_by = :createdBy and enabled = true and file_node_id = :fileNodeId
        order by created_at desc
        """,
    )
    fun findActiveByCreatorAndFileNode(createdBy: Long, fileNodeId: Long): List<ShareLink>

    @Modifying
    @Query(
        """
        delete from share_links
        where file_node_id = :fileNodeId
        """,
    )
    fun deleteByFileNodeId(fileNodeId: Long)
}
