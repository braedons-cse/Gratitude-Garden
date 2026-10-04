package com.gratitudegarden.app.di

import android.content.Context
import com.gratitudegarden.app.BuildConfig
import com.gratitudegarden.app.data.ConnectivityMonitor
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.PhotoPreparer
import com.gratitudegarden.app.data.WorkManagerOutboxScheduler
import com.gratitudegarden.app.data.local.GardenDatabase
import com.gratitudegarden.app.util.PwnedPasswords
import com.gratitudegarden.app.widget.WidgetSync
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manual service locator. Holds the singleton [SupabaseClient] (Auth, Postgrest, Storage),
 * the local [GardenDatabase], the [ConnectivityMonitor], the [GardenRepository], which
 * hands its journal outbox to WorkManager, and the home-screen widget's [WidgetSync].
 */
class AppContainer(private val context: Context) {

    // Not private: the staging flavor builds its admin repository on this same client.
    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        // The emulator's NAT can be slow; 10s (the default) is too tight.
        requestTimeout = 30.seconds
        // Partial @Serializable DTOs only map the columns we use.
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
        install(Auth)        // session persisted automatically (SettingsSessionManager)
        install(Postgrest)
        install(Storage)     // entry photos
    }

    // Lazy so the file isn't opened until a repository first needs it.
    val database: GardenDatabase by lazy { GardenDatabase.create(context) }

    val connectivity: ConnectivityMonitor by lazy { ConnectivityMonitor(context) }

    val gardenRepository: GardenRepository by lazy {
        GardenRepository(supabase, database, connectivity, photoDir(context), WorkManagerOutboxScheduler(context))
    }

    /** Turns a picked or captured image into the photo that is stored. */
    val photoPreparer: PhotoPreparer by lazy { PhotoPreparer(context) }

    // Lazy, and only touched once a widget exists (see WidgetSync).
    val widgetSync: WidgetSync by lazy { WidgetSync(context, gardenRepository) }

    // Its own small client: only sign-up uses it, and it talks to Have I Been Pwned, not Supabase.
    val pwnedPasswords: PwnedPasswords by lazy { PwnedPasswords(HttpClient(OkHttp)) }
}

/** Entry photos on the device. Must match the `photos/` exclusions in the backup rules. */
fun photoDir(context: Context) = File(context.filesDir, "photos")
