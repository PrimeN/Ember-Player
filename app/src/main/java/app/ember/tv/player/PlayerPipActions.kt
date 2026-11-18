package app.ember.tv.player

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import app.ember.player.PrivateMedia
import app.ember.tv.R
import java.io.Closeable

/** Actions belong to this activity and playback visit; old windows cannot control a new video. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlayerPipActions(private val activity:PlayerActivity):BroadcastReceiver(),Closeable {
    private val token=java.util.UUID.randomUUID().toString()
    private var visit=-1L
    private val pending=mutableMapOf<String,PendingIntent>()
    private var closed=false
    init {ContextCompat.registerReceiver(activity,this,IntentFilter(ACTION).apply {
        addDataScheme("ember");addDataAuthority("pip",null)
    },ContextCompat.RECEIVER_NOT_EXPORTED)}
    fun actions():List<RemoteAction> {
        val p=activity.player ?: return emptyList()
        if(closed || activity.privatePlayback || !activity.configInitialized)return emptyList()
        if(visit!=activity.playbackVisit){pending.values.forEach {it.cancel()};pending.clear();visit=activity.playbackVisit}
        val seconds=(TouchPlaybackMath.step(activity.config.touch.seekSeconds)/1000).toInt()
        val playing=p.playWhenReady
        fun action(verb:String,drawable:Int,title:String,enabled:Boolean):RemoteAction {
            val intent=pending.getOrPut(verb) {
                val data=Uri.Builder().scheme("ember").authority("pip").appendPath(token).appendPath(visit.toString()).appendPath(verb).build()
                PendingIntent.getBroadcast(activity,verb.hashCode(),Intent(ACTION).setPackage(activity.packageName).setData(data)
                    .putExtra("token",token).putExtra("visit",visit).putExtra("command",verb),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            return RemoteAction(Icon.createWithResource(activity,drawable),title,title,intent).apply {isEnabled=enabled}
        }
        val seek=p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        return listOf(
            action("back",android.R.drawable.ic_media_rew,activity.resources.getQuantityString(R.plurals.ui_pip_rewind,seconds,seconds),seek),
            action("toggle",if(playing)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,activity.getString(if(playing)R.string.ui_pause else R.string.ui_play),p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)),
            action("forward",android.R.drawable.ic_media_ff,activity.resources.getQuantityString(R.plurals.ui_pip_forward,seconds,seconds),seek),
        )
    }
    override fun onReceive(context:Context,intent:Intent) {
        if(closed || intent.action!=ACTION || intent.getStringExtra("token")!=token || intent.getLongExtra("visit",-1)!=activity.playbackVisit ||
            !activity.pictureInPictureEntered && !activity.isInPictureInPictureMode || activity.isFinishing || activity.privatePlayback ||
            !activity.prefsInitialized || !activity.contentPlaybackAllowed() || PrivateMedia.hidden(activity.currentUrl.orEmpty()))return
        val p=activity.player ?: return
        when(intent.getStringExtra("command")) {
            "toggle"->if(p.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)){if(p.playWhenReady)p.pause() else p.play()}
            "back","forward"->if(p.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                val delta=TouchPlaybackMath.step(activity.config.touch.seekSeconds)*if(intent.getStringExtra("command")=="back")-1 else 1
                val target=PlaybackSeeking.target(p.currentPosition,delta,p.duration)
                val engine=activity.playbackEngine
                if(engine!=null)engine.navigate(target,SeekPreference.fast(activity.config.seekMode),false)
                else PlaybackSeeking.seek(p,target,SeekPreference.fast(activity.config.seekMode))
            }
        }
        activity.updatePictureInPicture()
    }
    override fun close() {
        if(closed)return
        closed=true;activity.unregisterReceiver(this)
        pending.values.forEach {it.cancel()};pending.clear()
    }
    companion object {private const val ACTION="app.ember.player.PIP_CONTROL"}
}
