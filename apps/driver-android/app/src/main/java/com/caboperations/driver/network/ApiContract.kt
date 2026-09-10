package com.caboperations.driver.network

import kotlinx.serialization.Serializable

@Serializable
data class SyncItem(
    val type: String,
    val clientTransactionId: String,
    val payload: String
)

@Serializable
data class SyncRequest(val transactions: List<SyncItem>)

@Serializable
data class SyncResult(
    val clientTransactionId: String,
    val accepted: Boolean,
    val status: String? = null,
    val error: String? = null
)

@Serializable
data class SyncResponse(val results: List<SyncResult>)
