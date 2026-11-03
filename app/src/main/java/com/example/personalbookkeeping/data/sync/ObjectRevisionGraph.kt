package com.example.personalbookkeeping.data.sync

class ObjectRevisionGraph(revisions: List<ObjectStorageRevision>) {
    val byId: Map<String, ObjectStorageRevision>
    val headsByRecord: Map<String, List<ObjectStorageRevision>>

    init {
        val codec = ObjectStorageRevisionCodec()
        revisions.forEach { revision ->
            require(revision.schemaVersion == 1 &&
                codec.create(revision.entry, revision.parentRevisionIds) == revision) { "Invalid revision content hash" }
        }
        require(revisions.map { it.revisionId }.distinct().size == revisions.size) { "Duplicate revision ID" }
        byId = revisions.associateBy { it.revisionId }
        val children = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(revision: ObjectStorageRevision) {
            if (revision.revisionId in visited) return
            require(visiting.add(revision.revisionId)) { "Revision cycle" }
            require(canonicalRecordId(revision.recordId) && revision.entry.id == revision.recordId)
            for (parentId in revision.parentRevisionIds) {
                val parent = byId[parentId] ?: error("Missing revision parent")
                require(parent.recordId == revision.recordId) { "Parent belongs to another record" }
                children += parentId
                visit(parent)
            }
            visiting.remove(revision.revisionId)
            visited += revision.revisionId
        }
        revisions.forEach(::visit)
        headsByRecord = revisions.filter { it.revisionId !in children }
            .groupBy { it.recordId }.mapValues { (_, values) -> values.sortedBy { it.revisionId } }
    }
}
