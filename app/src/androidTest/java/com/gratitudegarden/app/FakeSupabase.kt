package com.gratitudegarden.app

import com.gratitudegarden.app.data.XP_EXTRA_ENTRY
import com.gratitudegarden.app.data.XP_FIRST_ENTRY
import com.gratitudegarden.app.data.levelForXp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
 * and the `or=(updated_at.gt.X,and(updated_at.eq.X,id.gt.Y))` cursor. The journal RPCs
 * behave like the real ones where the outbox depends on it: a submit is recognised by its
 * id and paid once, and a repeated delete succeeds.
 *
 * Storage is the entry-photos bucket and nothing more: upload (with upsert), delete by name,
 * and the authenticated download, with set_entry_photo refusing a file that isn't there.
 */
class FakeSupabase {
    val userId = "u1"

    /** Every request as `METHOD /path?query`, in order. */
    val requests = CopyOnWriteArrayList<String>()

    /** Throw an IOException for every request, like a phone in airplane mode. */
    @Volatile var offline = false

    /** Throw an IOException for RPCs only; table reads still work. */
    @Volatile var rpcsFail = false

    /** Carry out each RPC, then lose the response: the client sees an IOException. */
    @Volatile var dropResponses = false

    /** Lose only the first answer to each distinct RPC call, so each is carried out twice. */
    @Volatile var loseEachFirstAnswer = false
    private val answersLost = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Refuse any submit or edit whose text contains this, as the server refuses bad text. */
    @Volatile var refuseText: String? = null

    /** Delay every response, so overlapping refreshes really overlap. */
    @Volatile var latencyMs = 0L

    var dailyCap = 10

    /** Submits that inserted a row and paid for it; a replay must not add one. */
    @Volatile var paidSubmits = 0

    var displayName = "Tester"
    var balance = 12

    /** Earned as the server pays it: the day's first entry, then the extras. Level follows. */
    @Volatile var xp = 0

    /** Banked freezes; `freeze_grant_month` is always this month, so there's no pending free one. */
    @Volatile var freezes = 0
    val frozenDays = CopyOnWriteArrayList<String>()
    var gardenName: String? = "My Garden"
    val plantIds = CopyOnWriteArrayList(listOf("p1", "p2"))
    val ownedItemIds = CopyOnWriteArrayList(listOf("i1", "i3"))

    /** The equipped backdrop; the signup starter (i3) until set_active_backdrop changes it. */
    @Volatile var activeBackdropId: String? = "i3"
    val entries = CopyOnWriteArrayList<Entry>()

    /** The entry-photos bucket: object name to bytes. */
    val storedPhotos: MutableMap<String, ByteArray> = java.util.concurrent.ConcurrentHashMap()

    val today: String = LocalDate.now().toString()

    /** The streak's last day in `user_stats`: another device's entry shows up here first. */
    @Volatile var lastEntryDate: String = today

    data class Entry(
        val id: String,
        var text: String,
        val entryDate: String,
        val createdAt: String,
        var updatedAt: String,
        var deletedAt: String? = null,
        val coins: Int = 5,
        val xp: Int = XP_FIRST_ENTRY,
        var photoPath: String? = null,
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

    /** Server-side delete, as from another device: `deleted_at` and a fresh `updated_at`. */
    fun deleteElsewhere(id: String) {
        entries.first { it.id == id }.apply { deletedAt = nextStamp(); updatedAt = deletedAt!! }
    }

    /** Server-side photo change, as from another device: the file is stored, the old one removed. */
    fun setPhotoElsewhere(id: String, path: String?, bytes: ByteArray = byteArrayOf(1, 2, 3)) {
        entries.first { it.id == id }.apply {
            photoPath?.let { storedPhotos.remove(it) }
            path?.let { storedPhotos[it] = bytes }
            photoPath = path
            updatedAt = nextStamp()
        }
    }

    /** Uploads of photos, as `METHOD /path` requests. */
    fun photoUploads() = requests.filter { it.contains("/storage/v1/object/$BUCKET/") && !it.startsWith("GET") }

    /** Server-side edit, as from another device: new text, fresh `updated_at`. */
    fun edit(id: String, text: String) {
        entries.first { it.id == id }.apply { this.text = text; updatedAt = nextStamp() }
    }

    fun requestsTo(table: String) = requests.filter { it.contains("/rest/v1/$table?") || it.endsWith("/rest/v1/$table") }

    /** Calls to the RPC [name], or to any RPC. */
    fun rpcCalls(name: String = "") = requests.filter { it.contains("/rest/v1/rpc/$name") }

    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        requests += "${request.method.value} $path?${request.url.encodedQuery}"
        if (latencyMs > 0) delay(latencyMs)
        if (offline) throw IOException("offline")
        if (rpcsFail && path.startsWith("/rest/v1/rpc/")) throw IOException("offline")

        // An RPC without parameters (buy_streak_freeze) goes out with an empty body.
        val body = if (path.startsWith("/rest/v1/rpc/")) request.body.toByteArray().decodeToString() else ""
        val args = if (body.isNotBlank()) Json.parseToJsonElement(body).jsonObject else JsonObject(emptyMap())
        fun arg(name: String) = args[name]?.jsonPrimitive?.contentOrNull

        // Respond, unless the answer is being lost: then the work is done and the reply isn't.
        fun reply(content: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData {
            val call = "$path ${arg("p_id") ?: arg("p_entry_id")}"
            if (dropResponses || (loseEachFirstAnswer && answersLost.add(call))) throw IOException("response lost")
            return respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        when {
            path == "/auth/v1/logout" -> respond("", HttpStatusCode.NoContent)
            path.startsWith("/storage/v1/object/authenticated/$BUCKET/") -> {
                val bytes = storedPhotos[path.removePrefix("/storage/v1/object/authenticated/$BUCKET/")]
                if (bytes == null) respond("""{"statusCode":"404","error":"not_found","message":"Object not found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
                else respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
            path.startsWith("/storage/v1/object/$BUCKET/") -> {
                val name = path.removePrefix("/storage/v1/object/$BUCKET/")
                val exists = name in storedPhotos
                if (exists && request.headers["x-upsert"] != "true") {
                    reply("""{"statusCode":"409","error":"Duplicate","message":"The resource already exists"}""", HttpStatusCode.Conflict)
                } else {
                    storedPhotos[name] = request.body.toByteArray()
                    reply("""{"Key":"$BUCKET/$name","Id":"obj-${name.hashCode()}"}""")
                }
            }
            path == "/storage/v1/object/$BUCKET" && request.method.value == "DELETE" -> {
                val names = Json.parseToJsonElement(request.body.toByteArray().decodeToString())
                    .jsonObject["prefixes"]!!.jsonArray.map { it.jsonPrimitive.content }
                names.forEach { storedPhotos.remove(it) }
                reply(names.joinToString(",", "[", "]") { """{"name":"$it"}""" })
            }
            path == "/rest/v1/rpc/set_entry_photo" -> {
                val photo = arg("p_photo_path")
                val entry = entries.firstOrNull { it.id == arg("p_entry_id") && it.deletedAt == null }
                when {
                    photo != null && !photo.startsWith("$userId/${arg("p_entry_id")}/") ->
                        reply(error("photo path not allowed"), HttpStatusCode.BadRequest)
                    photo != null && photo !in storedPhotos -> reply(error("photo not uploaded"), HttpStatusCode.BadRequest)
                    entry == null -> reply(error("entry not found"), HttpStatusCode.BadRequest)
                    else -> {
                        entry.photoPath = photo
                        entry.updatedAt = nextStamp()
                        reply(json(entry))
                    }
                }
            }
            path == "/rest/v1/rpc/own_photo_objects" -> {
                val prefix = "$userId/" + (arg("p_entry_id")?.let { "$it/" } ?: "")
                // PostgREST's shape for a setof-scalar function; the repository reads either.
                reply(storedPhotos.keys.filter { it.startsWith(prefix) }.sorted().joinToString(",", "[", "]") { "\"$it\"" })
            }
            path == "/rest/v1/rpc/submit_gratitude_entry" -> {
                val id = arg("p_id")!!
                val text = arg("p_entry_text")!!
                val existing = entries.firstOrNull { it.id == id }
                when {
                    existing != null -> reply(json(existing)) // a replay: the first row, unpaid
                    refused(text) -> reply(error("entry text required"), HttpStatusCode.BadRequest)
                    else -> {
                        val writtenAt = Instant.parse(arg("p_written_at")!!)
                        val day = writtenAt.atZone(ZoneId.of(arg("p_time_zone")!!)).toLocalDate().toString()
                        val entry = Entry(
                            id = id,
                            text = text,
                            entryDate = day,
                            createdAt = format.format(writtenAt),
                            updatedAt = nextStamp(),
                            xp = if (entries.none { it.entryDate == day }) XP_FIRST_ENTRY else XP_EXTRA_ENTRY,
                        )
                        entries += entry
                        balance += entry.coins
                        xp += entry.xp
                        paidSubmits++
                        reply(json(entry))
                    }
                }
            }
            path == "/rest/v1/rpc/edit_gratitude_entry" -> {
                val text = arg("p_new_text")!!
                val entry = entries.firstOrNull { it.id == arg("p_entry_id") && it.deletedAt == null }
                when {
                    entry == null -> reply(error("entry not found"), HttpStatusCode.BadRequest)
                    refused(text) -> reply(error("entry text required"), HttpStatusCode.BadRequest)
                    else -> {
                        entry.text = text
                        entry.updatedAt = nextStamp()
                        reply(json(entry))
                    }
                }
            }
            path == "/rest/v1/rpc/delete_gratitude_entry" -> {
                val entry = entries.firstOrNull { it.id == arg("p_entry_id") }
                if (entry == null) {
                    reply(error("entry not found"), HttpStatusCode.BadRequest)
                } else {
                    // Deleting a deleted entry succeeds and changes nothing.
                    if (entry.deletedAt == null) {
                        val stamp = nextStamp()
                        entry.deletedAt = stamp
                        entry.updatedAt = stamp
                        entry.photoPath = null
                    }
                    reply("", HttpStatusCode.NoContent)
                }
            }
            path == "/rest/v1/rpc/buy_streak_freeze" -> when {
                freezes >= 2 -> reply(error("streak freeze limit reached"), HttpStatusCode.BadRequest)
                balance < 50 -> reply(error("insufficient coins (need 50, have $balance)"), HttpStatusCode.BadRequest)
                else -> {
                    balance -= 50
                    freezes++
                    reply("$freezes")
                }
            }
            path == "/rest/v1/rpc/set_active_backdrop" -> {
                val id = arg("p_item_id")
                if (id !in ownedItemIds) {
                    reply(error("you do not own this backdrop"), HttpStatusCode.BadRequest)
                } else {
                    activeBackdropId = id
                    reply(table("gardens", Parameters.Empty).removeSurrounding("[", "]"))
                }
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
        "profiles" -> """[{"display_name":"$displayName","level":${levelForXp(xp)},"xp":$xp,"is_admin":false}]"""
        "coin_wallets" -> """[{"balance":$balance}]"""
        "user_stats" -> """[{"total_entries":${entries.count { it.deletedAt == null }},"current_streak":2,"longest_streak":5,""" +
            """"last_entry_date":"$lastEntryDate","streak_freezes":$freezes,"freeze_grant_month":"${LocalDate.now().withDayOfMonth(1)}"}]"""
        "streak_frozen_days" -> frozenDays.joinToString(",", "[", "]") { """{"day":"$it"}""" }
        "user_settings" -> """[{"notif_prompt_seen":false,"daily_entry_cap":$dailyCap}]"""
        "gardens" -> gardenName?.let {
            val backdrop = activeBackdropId?.let { id -> "\"$id\"" } ?: "null"
            """[{"id":"g1","name":"$it","grid_rows":6,"grid_cols":5,"active_backdrop_item_id":$backdrop}]"""
        } ?: "[]"
        "garden_plants" -> plantIds.mapIndexed { i, id ->
            """{"id":"$id","item_id":"i1","grid_x":$i,"grid_y":0,"growth_stage":"seedling","health":"healthy"}"""
        }.joinToString(",", "[", "]")
        "items" -> """[{"id":"i1","slug":"sunflower","category":"seed","name":"Sunflower","price_coins":5},""" +
            """{"id":"i2","slug":"tulip","category":"seed","name":"Tulip","price_coins":8},""" +
            """{"id":"i3","slug":"backdrop.cottage_meadow","category":"backdrop","name":"Cottage Meadow",""" +
            """"price_coins":0,"is_purchasable":false,"is_starter":true},""" +
            """{"id":"i4","slug":"backdrop.misty_forest","category":"backdrop","name":"Misty Forest",""" +
            """"price_coins":160,"level_required":2}]"""
        "user_inventory" -> ownedItemIds.joinToString(",", "[", "]") { """{"item_id":"$it"}""" }
        "gratitude_entries" -> entriesPage(params)
        else -> error("unexpected table $name")
    }

    private fun entriesPage(params: Parameters): String {
        // A lookup of one entry (discarding a refused change), not the sync.
        params["id"]?.let { f -> return entries.filter { f == "eq.${it.id}" }.joinToString(",", "[", "]") { json(it) } }
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
        return rows.joinToString(",", "[", "]") { json(it) }
    }

    private fun json(e: Entry) =
        """{"id":"${e.id}","entry_text":"${e.text}","input_method":"text","coins_awarded":${e.coins},"xp_awarded":${e.xp},""" +
            """"entry_date":"${e.entryDate}","created_at":"${e.createdAt}","updated_at":"${e.updatedAt}",""" +
            """"deleted_at":${e.deletedAt?.let { "\"$it\"" } ?: "null"},"photo_path":${e.photoPath?.let { "\"$it\"" } ?: "null"}}"""

    private fun refused(text: String) = refuseText?.let { it in text } == true

    /** A PostgREST error body, as for a `raise exception` in an RPC. */
    private fun error(message: String) =
        """{"code":"P0001","message":"$message","details":null,"hint":null}"""


    private var clock = 1_000_000L
    private fun nextStamp() = stamp(clock++)

    companion object {
        const val BUCKET = "entry-photos"

        private val base = Instant.parse("2026-09-24T00:00:00Z")
        private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx").withZone(ZoneOffset.UTC)

        /** A Postgres-style timestamp [micros] after a fixed base, e.g. `2026-09-24T00:00:00.000001+00:00`. */
        fun stamp(micros: Long): String = format.format(base.plus(micros, ChronoUnit.MICROS))
    }
}
