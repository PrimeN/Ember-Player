package app.ember.player

import android.content.res.Resources

/** Resolve model labels when a screen renders, so changing the locale keeps saved IDs stable. */
internal fun List<Pair<String,Int>>.localized(resources:Resources)=map {it.first to resources.getString(it.second)}
