package com.whoami22888.mediavault.model

data class MediaAnnotation(
    val uriText: String,
    val personNames: Set<String> = emptySet(),
    val locationLabel: String? = null,
    val customContentTags: Set<String> = emptySet(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
