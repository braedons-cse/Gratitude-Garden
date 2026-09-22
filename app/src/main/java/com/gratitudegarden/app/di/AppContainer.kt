package com.gratitudegarden.app.di

import android.content.Context
import com.gratitudegarden.app.BuildConfig
import com.gratitudegarden.app.data.GardenRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/**
 * Manual service locator. Holds the singleton [SupabaseClient] (Auth + Postgrest)
 * and the [GardenRepository] built on top of it.
 */
class AppContainer(@Suppress("unused") private val context: Context) {

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
    }

    val gardenRepository: GardenRepository by lazy { GardenRepository(supabase) }
}
