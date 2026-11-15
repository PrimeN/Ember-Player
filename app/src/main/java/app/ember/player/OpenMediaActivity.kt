package app.ember.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import app.ember.tv.player.PlayRequest
import app.ember.tv.player.PlayerActivity

/** The exported boundary passes media grants, not private session/profile commands. */
class OpenMediaActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val uri=intent.data
        if(uri==null || !ExternalMedia.allowed(uri.toString())) {
            Toast.makeText(this,"Choose a media file or a supported stream URL.",Toast.LENGTH_LONG).show();finish();return
        }
        try {
            val next=Intent(this,PlayerActivity::class.java).setData(uri)
                .putExtra(PlayRequest.EXTRA_URL,uri.toString())
                .putExtra(PlayRequest.EXTRA_RESUME_KEY,app.ember.tv.player.QueueMedia(uri.toString(),"Media").key)
                .putExtra(PlayRequest.EXTRA_TITLE,intent.getStringExtra("title")?.take(180) ?: uri.lastPathSegment?.take(180) ?: "Media")
            next.clipData=intent.clipData
            next.addFlags(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(next)
        } catch(_:Exception){Toast.makeText(this,"Could not open this media.",Toast.LENGTH_LONG).show()}
        finish()
    }
}
internal object ExternalMedia {
    fun allowed(value:String):Boolean=runCatching {
        require(value.length in 1..16_384 && value.none {it=='\r'||it=='\n'||it=='\u0000'})
        val uri=java.net.URI(value)
        when(uri.scheme?.lowercase()) {
            "content"->!uri.authority.isNullOrBlank()
            "http","https","rtsp","rtmp","rtp","udp","tcp"->!uri.host.isNullOrBlank() && (uri.port==-1 || uri.port in 1..65535)
            else->false
        }
    }.getOrDefault(false)
}
