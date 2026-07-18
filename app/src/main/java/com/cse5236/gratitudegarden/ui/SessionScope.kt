package com.cse5236.gratitudegarden.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * A [ViewModelStoreOwner] scoped to a single signed-in session.
 *
 * Implements [HasDefaultViewModelProviderFactory] so that `viewModel(factory = ...)` calls
 * made under this owner pick up [defaultViewModelCreationExtras], which carries
 * `APPLICATION_KEY`. That key is required by the tab/admin VM factories'
 * `repo()`/`gardenApp()`/`adminRepo()` extensions (see [ViewModelExt]); without it they
 * would NPE at VM creation.
 */
private class SessionViewModelStoreOwner(
    application: Application,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()

    // Required by the interface but unused: every call site passes an explicit factory.
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }
}

/**
 * Provides a session-scoped [ViewModelStoreOwner] to [content], re-created whenever [userId]
 * changes and cleared when it leaves composition.
 *
 * The signed-in tab/admin ViewModels call `viewModel()` with no explicit owner, so without
 * this they'd resolve to the Activity's store and outlive a sign-out — leaking user A's
 * cached state to user B on the next sign-in. Scoping them here evicts (and `onCleared()`s)
 * every one of them between users.
 */
@Composable
fun SessionScope(userId: String?, content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    key(userId) {
        val owner = remember { SessionViewModelStoreOwner(app) }
        DisposableEffect(owner) {
            onDispose { owner.viewModelStore.clear() }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            content()
        }
    }
}
