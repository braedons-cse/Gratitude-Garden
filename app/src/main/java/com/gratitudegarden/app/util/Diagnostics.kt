package com.gratitudegarden.app.util

import android.content.Context
import com.gratitudegarden.app.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * Crash and ANR reports, plus the few caught errors worth knowing about (roadmap 0.6).
 *
 * Off unless the build has a DSN (`SENTRY_DSN` in local.properties), and every call here is
 * then a no-op. Nothing identifies the person: no account ID, no IP (`sendDefaultPii` stays
 * off), no screenshots or view hierarchy. Each event is passed through [scrub] on the device
 * before it leaves, because supabase-kt puts the request URL and headers into its exception
 * messages, and Postgres quotes a whole row, journal text included, when it rejects one.
 */
object Diagnostics {

    fun init(context: Context) {
        if (BuildConfig.SENTRY_DSN.isEmpty()) return
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = "${BuildConfig.FLAVOR}-${BuildConfig.BUILD_TYPE}"
            options.isSendDefaultPii = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrubEvent(event) }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { crumb, _ -> scrubCrumb(crumb) }
        }
    }

    /** A caught error that shouldn't have happened. [action] groups it, e.g. the screen's fallback copy. */
    fun report(e: Throwable, action: String) {
        Sentry.captureException(e) { scope -> scope.setTag("action", action) }
    }

    /** Something went wrong without an exception to show for it. */
    fun warn(message: String) {
        Sentry.captureMessage(message, SentryLevel.WARNING)
    }

    private fun scrubEvent(event: SentryEvent): SentryEvent {
        event.exceptions?.forEach { it.value = scrub(it.value) }
        event.message?.let { m ->
            m.message = scrub(m.message)
            m.formatted = scrub(m.formatted)
        }
        event.breadcrumbs?.forEach { scrubCrumb(it) }
        return event
    }

    private fun scrubCrumb(crumb: Breadcrumb): Breadcrumb {
        crumb.message = scrub(crumb.message)
        for ((key, value) in crumb.data.entries.toList()) {
            if (value is String) crumb.setData(key, scrub(value))
        }
        return crumb
    }
}

// supabase-kt's RestException puts the request headers (API key and the user's token) on a
// line of their own.
private val HEADERS_LINE = Regex("""(?m)^\s*Headers:.*$\n?""")
// Postgres check failures quote the rejected row, and the row can run over several lines.
// Greedy, so it runs to the *last* `URL:` line, the one supabase-kt adds: journal text can
// have a line of its own that starts with `URL:`. With no URL line, it runs to the end.
private val FAILING_ROW = Regex("""(?s)Failing row contains .*(?=\nURL:)|Failing row contains .*""")
// Unique and foreign-key failures quote the key's value.
private val KEY_VALUE = Regex("""(?m)(Key \([^)]*\)=).*$""")
private val JWT = Regex("""eyJ[\w-]+\.[\w-]+\.[\w-]*""")
private val BEARER = Regex("""(?i)(Bearer\s+)\S+""")
// Postgrest filters carry IDs: `?id=eq.<uuid>`.
private val URL_QUERY = Regex("""(https?://[^\s?]+)\?\S*""")
// User, entry and photo IDs; photo paths are `{user}/{entry}/{photo}.jpg`.
private val UUID = Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""")
private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

/** [text] with anything that could identify someone or quote their journal taken out. */
internal fun scrub(text: String?): String? = text
    ?.replace(HEADERS_LINE, "")
    ?.replace(FAILING_ROW, "Failing row contains [row]")
    ?.replace(KEY_VALUE, "$1[value]")
    ?.replace(JWT, "[token]")
    ?.replace(BEARER, "$1[token]")
    ?.replace(URL_QUERY, "$1?[query]")
    ?.replace(UUID, "[id]")
    ?.replace(EMAIL, "[email]")
