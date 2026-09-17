package com.gratitudegarden.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walk the ContextWrapper chain to find the hosting Activity. Needed for
 * `shouldShowRequestPermissionRationale`, which distinguishes a normal denial
 * (re-prompt) from a permanent "don't ask again" block (send to Settings).
 */
fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
