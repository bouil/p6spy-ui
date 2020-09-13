package net.bouillon.p6spy.ui

data class SqlLog(
    val connectionId: Int = 0,
    val now: String? = null,
    val elapsed: Long = 0,
    val category: String? = null,
    val prepared: String? = null,
    val sql: String? = null,
    val url: String? = null
)