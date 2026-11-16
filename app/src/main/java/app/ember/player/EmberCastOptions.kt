package app.ember.player

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.CastMediaOptions

/** CastContext is created only when the user opens the receiver screen. */
class EmberCastOptions:OptionsProvider {
    override fun getCastOptions(context:Context):CastOptions=CastOptions.Builder()
        .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        .setEnableReconnectionService(false).setResumeSavedSession(false)
        .setSessionTransferEnabled(false).setRemoteToLocalEnabled(false)
        .setStopReceiverApplicationWhenEndingSession(true)
        .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).setMediaSessionEnabled(false).build()).build()
    override fun getAdditionalSessionProviders(context:Context):List<SessionProvider>?=null
}
internal object CastRuntime {
    @Volatile var context:CastContext?=null
    val executor by lazy {java.util.concurrent.Executors.newSingleThreadExecutor {r->Thread(r,"Ember receiver setup").apply {isDaemon=true}}}
}
internal open class CastSessionObserver:SessionManagerListener<CastSession> {
    override fun onSessionStarting(session:CastSession){}
    override fun onSessionStarted(session:CastSession,id:String){}
    override fun onSessionStartFailed(session:CastSession,error:Int){}
    override fun onSessionEnding(session:CastSession){}
    override fun onSessionEnded(session:CastSession,error:Int){}
    override fun onSessionResuming(session:CastSession,id:String){}
    override fun onSessionResumed(session:CastSession,suspended:Boolean){}
    override fun onSessionResumeFailed(session:CastSession,error:Int){}
    override fun onSessionSuspended(session:CastSession,error:Int){}
}
