package com.gratitudegarden.app.util

/**
 * The public pages Play requires, served by GitHub Pages from the gratitude-garden-site repo.
 * Their source lives in `site/` in this repo. Both URLs are also entered in Play Console, so
 * moving the pages means changing them there too.
 */
private const val SITE = "https://braedons-cse.github.io/gratitude-garden-site"

internal const val PRIVACY_POLICY_URL = "$SITE/privacy.html"

/** Deletes an account without the app: sign in, confirm, gone. Play asks for this one. */
internal const val DELETE_ACCOUNT_URL = "$SITE/delete-account.html"
