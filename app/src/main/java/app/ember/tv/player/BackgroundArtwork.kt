package app.ember.tv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream

internal object BackgroundArtwork {
    fun read(context:Context,url:String):ByteArray?=runCatching {
        val uri=Uri.parse(url)
        if(uri.scheme !in setOf("content","file")||app.ember.player.PrivateMedia.hidden(url))return null
        val retriever=MediaMetadataRetriever()
        val bytes=try {retriever.setDataSource(context,uri);retriever.embeddedPicture}finally {retriever.release()} ?: return null
        if(bytes.size>4*1024*1024)return null
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth.toLong()*bounds.outHeight>40_000_000)return null
        val options=BitmapFactory.Options().apply {while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>512)inSampleSize*=2}
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: return null
        try {ByteArrayOutputStream().use {out->bitmap.compress(Bitmap.CompressFormat.JPEG,80,out);out.toByteArray().takeIf {it.size<=512*1024}}}finally {bitmap.recycle()}
    }.getOrNull()
}
