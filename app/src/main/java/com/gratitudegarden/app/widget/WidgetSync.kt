package com.gratitudegarden.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.GardenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the widget's [state] current for the life of the process, and pushes each change to
 * the widgets on the home screen. Changes come from the repository's tables (a submit, a
 * sync, a sign-out) and from [refresh] (midnight, a clock or time zone change).
 *
 * Only started when a widget exists: without one there's no reason to open the database in,
 * say, a process the reminder alarm started.
 */
class WidgetSync(private val context: Context, private val repo: GardenRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val publishing = Mutex()

    private val _state = MutableStateFlow<WidgetState?>(null)

    /** Null until the first read, and while nobody's session has been read yet. */
    val state: StateFlow<WidgetState?> = _state.asStateFlow()

    /** Idempotent: every entry point (the app, the widget, its alarm) calls it. */
    @OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // A submit touches several tables in a burst; one read after it settles.
            merge(repo.widgetChanges(), refreshes)
                .debounce(300.milliseconds)
                .mapLatest { snapshot() }
                .collect { publish(it) }
        }
    }

    /** Read again with today's date: midnight, or the clock or zone changed. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** [refresh], waited for: a receiver's process may not outlive its onReceive otherwise. */
    suspend fun refreshNow() {
        publish(snapshot())
    }

    private suspend fun publish(next: WidgetState?) {
        if (next == null) return
        publishing.withLock {
            if (next == _state.value) return
            _state.value = next
            // A running widget session follows [state] itself; this starts one where none is.
            if (hasWidgets(context)) GardenWidget().updateAll(context)
        }
    }

    private suspend fun snapshot(): WidgetState? {
        val session = withTimeoutOrNull(3.seconds) { repo.session.first { it !is AppSession.Loading } }
        return when (session) {
            null, AppSession.Loading -> null
            AppSession.SignedOut -> WidgetState.SignedOut
            is AppSession.SignedIn -> widgetStateFrom(
                stats = repo.observeStats().first(),
                streakToday = LocalDate.now(),
                garden = repo.observeGarden().first(),
                plants = repo.observePlants().first(),
                slugs = repo.observeCatalog().first().associate { it.id to it.slug },
                usedToday = repo.observeEntriesTodayCount().first(),
                dailyCap = repo.observeDailyCap().first(),
            )
        }
    }

    companion object {
        /** Whether any Gratitude Garden widget is on a home screen. */
        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, GardenWidgetReceiver::class.java))
                .isNotEmpty()
    }
}
