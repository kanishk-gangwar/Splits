package com.kanishk.splits.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A *synchronous* SqlDriver for the browser, backed by sql.js (SQLite compiled to WebAssembly)
 * running on the main thread.
 *
 * SQLDelight's own web driver runs SQLite in a worker and is async-only, which would mean
 * regenerating every query as `suspend` and rewriting the repository for all three platforms.
 * sql.js is synchronous once loaded, so this driver keeps the exact API Android and iOS use.
 * The database lives in memory and the JS bridge (sqlite-bridge.js) writes a snapshot of it to
 * IndexedDB shortly after every write.
 *
 * Values cross the JS boundary as JSON, which is plenty for this app's row counts. Integers
 * are JS numbers on the way back, so anything beyond 2^53 would lose precision; the schema
 * only stores minor-unit amounts and epoch millis, both far below that.
 */
class WebSqlDriver : SqlDriver {
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()
    private var transaction: Transaction? = null

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val rows = Json.parseToJsonElement(jsQuery(sql, bind(parameters, binders))).jsonArray
        return mapper(JsonCursor(rows))
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = QueryResult.Value(jsRun(sql, bind(parameters, binders)).toLong())

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val enclosing = transaction
        val created = Transaction(enclosing)
        transaction = created
        if (enclosing == null) jsRun("BEGIN TRANSACTION", "[]")
        return QueryResult.Value(created)
    }

    override fun currentTransaction(): Transacter.Transaction? = transaction

    private inner class Transaction(
        override val enclosingTransaction: Transaction?,
    ) : Transacter.Transaction() {
        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            if (enclosingTransaction == null) {
                jsRun(if (successful) "COMMIT" else "ROLLBACK", "[]")
            }
            transaction = enclosingTransaction
            return QueryResult.Unit
        }
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        queryKeys.forEach { listeners.getOrPut(it) { linkedSetOf() }.add(listener) }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        queryKeys.forEach { listeners[it]?.remove(listener) }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        queryKeys.flatMap { listeners[it].orEmpty() }.toSet().forEach { it.queryResultsChanged() }
    }

    override fun close() = Unit

    private fun bind(parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): String {
        if (parameters == 0 || binders == null) return "[]"
        val statement = JsonStatement(parameters)
        statement.binders()
        return JsonArray(statement.values.toList()).toString()
    }
}

private class JsonStatement(size: Int) : SqlPreparedStatement {
    val values = Array<JsonElement>(size) { JsonNull }

    override fun bindBytes(index: Int, bytes: ByteArray?) {
        // The schema has no BLOB columns; failing loudly beats storing something unreadable.
        if (bytes != null) error("BLOB parameters are not supported by WebSqlDriver")
    }

    override fun bindLong(index: Int, long: Long?) {
        values[index] = JsonPrimitive(long)
    }

    override fun bindDouble(index: Int, double: Double?) {
        values[index] = JsonPrimitive(double)
    }

    override fun bindString(index: Int, string: String?) {
        values[index] = JsonPrimitive(string)
    }

    override fun bindBoolean(index: Int, boolean: Boolean?) {
        values[index] = JsonPrimitive(boolean?.let { if (it) 1L else 0L })
    }
}

private class JsonCursor(private val rows: JsonArray) : SqlCursor {
    private var position = -1

    override fun next(): QueryResult<Boolean> = QueryResult.Value(++position < rows.size)

    private fun cell(index: Int): JsonPrimitive? {
        val value = rows[position].jsonArray[index]
        return if (value is JsonNull) null else value.jsonPrimitive
    }

    override fun getString(index: Int): String? = cell(index)?.contentOrNull

    override fun getLong(index: Int): Long? = cell(index)?.let { it.longOrNull ?: it.double.toLong() }

    override fun getDouble(index: Int): Double? = cell(index)?.double

    override fun getBoolean(index: Int): Boolean? = getLong(index)?.let { it != 0L }

    override fun getBytes(index: Int): ByteArray? =
        cell(index)?.let { error("BLOB columns are not supported by WebSqlDriver") }
}

private fun jsQuery(sql: String, params: String): String =
    js("globalThis.splitsSql.query(sql, params)")

private fun jsRun(sql: String, params: String): Int =
    js("globalThis.splitsSql.run(sql, params)")
