package com.remotepair.controller.files

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
)

@Serializable
data class ListResult(val path: String, val entries: List<FileEntry>)
