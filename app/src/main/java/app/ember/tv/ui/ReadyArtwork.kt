package app.ember.tv.ui

import android.graphics.drawable.Drawable
import app.ember.tv.data.Meta

/** Shared artwork coordinates for translucent surfaces. */
data class ReadyArtwork(val meta:Meta?,val drawable:Drawable?,val focus:HeroFocus?=null,val viewport:HeroViewport?=null,val source:String?=null)
