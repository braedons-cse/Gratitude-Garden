package com.gratitudegarden.app

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for the Supabase backend, served through Ktor's [MockEngine] so the real
 * repository and supabase-kt run unmodified against it.
 *
 * It answers only what GardenRepository asks for, for one user, and implements just enough
 * of PostgREST to be honest about the entry sync: `limit`, ordering by `(updated_at, id)`,
 * and the `or=(updated_at.gt.X,and(updated_at.eq.X,id.gt.Y))` cursor.
 */
class FakeSupabase {
    val userId = "u1"

    /** Every request as `METHOD /path?query`, in order. */
    val requests = CopyOnWriteArrayList<String>()

    /** Throw an IOException for every request, like a phone in airplane mode. */
    @Volatile var offline = false

    /** Delay every response, so overlapping refreshes really overlap. */
    @Volatile var latencyMs = 0L

    var displayName = "Tester"
    var balance = 12
    var gardenName: String? = "My Garden"
    val plantIds = CopyOnWriteArrayList(listOf("p1", "p2"))
    val ownedItemIds = CopyOnWriteArrayList(listOf("i1"))
    val entries = CopyOnWriteArrayList<Entry>()

    val today: String = LocalDate.now().toString()

    data class Entry(
        val id: String,
        var text: String,
        val entryDate: String,
        val createdAt: String,
        var updatedAt: String,
        var deletedAt: String? = null,
    )

    /** Add [count] entries dated today; ids and timestamps ascend. */
    fun addEntries(count: Int, idPrefix: String = "e") {
        val start = entries.size
        repeat(count) { i ->
            val n = start + i
            val t = stamp(n.toLong())
            entries += Entry(id = "%s%05d".format(idPrefix, n), text = "thanks #$n", entryDate = today, createdAt = t, updatedAt = t)
        }
    }

    /** Server-side edit, as from another device: new text, fresh `updated_at`. */
    fun edit(id: String, text: String) {
        entries.first { it.id == id }.apply { this.text = text; updatedAt = nextStamp() }
    }

    fun requestsTo(table: String) = requests.filter { it.contains("/rest/v1/$table?") || it.endsWith("/rest/v1/$table") }

    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        requests += "${request.method.value} $path?${request.url.encodedQuery}"
        if (latencyMs > 0) delay(latencyMs)
        if (offline) throw IOException("offline")

        when {
            path == "/auth/v1/logout" -> respond("", HttpStatusCode.NoContent)
            path == "/rest/v1/rpc/delete_gratitude_entry" -> {
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                val id = body.getValue("p_entry_id").jsonPrimitive.content
                val stamp = nextStamp()
                entries.first { it.id == id }.apply { deletedAt = stamp; updatedAt = stamp }
                respond("", HttpStatusCode.NoContent)
            }
            path.startsWith("/rest/v1/") -> respond(
                content = table(path.removePrefix("/rest/v1/"), request.url.parameters),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> respond("no route for $path", HttpStatusCode.NotFound)
        }
    }

    private fun table(name: String, params: Parameters): String = when (name) {
        "profiles" -> """[{"display_name":"$displayName","level":1,"xp":0,"is_admin":false}]"""
        "coin_wallets" -> """[{"balance":$balance}]"""
        "user_stats" -> """[{"total_entries":${entries.count { it.deletedAt == null }},"current_streak":2,"longest_streak":5,"last_entry_date":"$today"}]"""
        "user_settings" -> """[{"notif_prompt_seen":false}]"""
        "gardens" -> gardenName?.let { """[{"id":"g1","name":"$it","grid_rows":6,"grid_cols":5}]""" } ?: "[]"
        "garden_plants" -> plantIds.mapIndexed { i, id ->
            """{"id":"$id","item_id":"i1","grid_x":$i,"grid_y":0,"growth_stage":"seedling","health":"healthy"}"""
        }.joinToString(",", "[", "]")
        "items" -> """[{"id":"i1","slug":"sunflower","category":"seed","name":"Sunflower","price_coins":5},""" +
            """{"id":"i2","slug":"tulip","category":"seed","name":"Tulip","price_coins":8}]"""
        "user_inventory" -> ownedItemIds.joinToString(",", "[", "]") { """{"item_id":"$it"}""" }
        "gratitude_entries" -> entriesPage(params)
        else -> error("unexpected table $name")
    }

    private fun entriesPage(params: Parameters): String {
        check(params["order"] == "updated_at.asc.nullslast,id.asc.nullslast") { "unexpected order ${params["order"]}" }
        var rows = entries.sortedWith(compareBy({ it.updatedAt }, { it.id }))
        params["or"]?.let { or ->
            val at = Regex("""updated_at\.gt\."?([^",)]+)""").find(or)!!.groupValues[1]
            val id = Regex("""id\.gt\."?([^",)]+)""").find(or)!!.groupValues[1]
            // Same-format timestamps compare correctly as strings (fake only; Postgres compares instants).
            rows = rows.filter { it.updatedAt > at || (it.updatedAt == at && it.id > id) }
        }
        // A plain timestamp filter too, so a timestamp-only cursor fails by losing rows
        // rather than by looping on the first page.
        params["updated_at"]?.let { f ->
            val (op, at) = f.split('.', limit = 2)
            rows = rows.filter { if (op == "gte") it.updatedAt >= at else it.updatedAt > at }
        }
        params["limit"]?.toInt()?.let { rows = rows.take(it) }
        return rows.joinToString(",", "[", "]") { e ->
            """{"id":"${e.id}","entry_text":"${e.text}","input_method":"text","coins_awarded":5,""" +
                """"entry_date":"${e.entryDate}","created_at":"${e.createdAt}","updated_at":"${e.updatedAt}",""" +
                """"deleted_at":${e.deletedAt?.let { "\"$it\"" } ?: "null"}}"""
        }
    }

    private var clock = 1_000_000L
    private fun nextStamp() = stamp(clock++)

    companion object {
        private val base = Instant.parse("2026-09-24T00:00:00Z")
        private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx").withZone(ZoneOffset.UTC)

        /** A Postgres-style timestamp [micros] after a fixed base, e.g. `2026-09-24T00:00:00.000001+00:00`. */
        fun stamp(micros: Long): String = format.format(base.plus(micros, ChronoUnit.MICROS))
    }
}
