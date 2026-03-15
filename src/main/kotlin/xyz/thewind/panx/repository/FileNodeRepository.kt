package xyz.thewind.panx.repository

import xyz.thewind.panx.domain.FileNode
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface FileNodeRepository : CrudRepository<FileNode, Long> {

    @Query(
        """
        select * from file_nodes
        where owner_id = :ownerId and deleted = false and parent_id is null
        order by node_type desc, name asc
        """,
    )
    fun findRootNodes(ownerId: Long): List<FileNode>

    @Query(
        """
        select * from file_nodes
        where owner_id = :ownerId and deleted = false and parent_id = :parentId
        order by node_type desc, name asc
        """,
    )
    fun findByOwnerIdAndParentId(ownerId: Long, parentId: Long): List<FileNode>

    @Query(
        """
        select * from file_nodes
        where owner_id = :ownerId and deleted = false and lower(name) like lower(concat('%', :keyword, '%'))
        order by updated_at desc
        """,
    )
    fun searchByOwnerIdAndKeyword(ownerId: Long, keyword: String): List<FileNode>

    @Query(
        """
        select * from file_nodes
        where owner_id = :ownerId and deleted = false and id = :id
        limit 1
        """,
    )
    fun findActiveByIdAndOwnerId(id: Long, ownerId: Long): FileNode?

    @Query(
        """
        select * from file_nodes
        where parent_id = :parentId and deleted = false
        order by node_type desc, name asc
        """,
    )
    fun findActiveChildren(parentId: Long): List<FileNode>

    @Query(
        """
        select * from file_nodes
        where owner_id = :ownerId and deleted = false and node_type = 'FOLDER'
        order by name asc
        """,
    )
    fun findAllFoldersByOwnerId(ownerId: Long): List<FileNode>

    @Query(
        """
        select count(*) > 0 from file_nodes
        where owner_id = :ownerId
          and deleted = false
          and name = :name
          and ((:parentId is null and parent_id is null) or parent_id = :parentId)
        """,
    )
    fun existsActiveSibling(ownerId: Long, parentId: Long?, name: String): Boolean

    @Query(
        """
        select count(*) > 0 from file_nodes
        where owner_id = :ownerId
          and deleted = false
          and name = :name
          and id <> :excludeId
          and ((:parentId is null and parent_id is null) or parent_id = :parentId)
        """,
    )
    fun existsActiveSiblingExcludingId(ownerId: Long, parentId: Long?, name: String, excludeId: Long): Boolean

    @Query(
        """
        select * from file_nodes
        where owner_id = :ownerId
          and deleted = false
          and name = :name
          and node_type = :nodeType
          and ((:parentId is null and parent_id is null) or parent_id = :parentId)
        limit 1
        """,
    )
    fun findActiveByOwnerIdAndParentIdAndNameAndNodeType(ownerId: Long, parentId: Long?, name: String, nodeType: String): FileNode?
}
