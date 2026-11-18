package app.ember.player

import android.content.Context
import app.ember.tv.data.LocalActivity
import app.ember.tv.data.Prefs
import app.ember.tv.player.*

/** Receiver reports can reset the position to zero when a title finishes. */
internal fun saveCastPosition(context:Context,request:CastRequest,status:ReceiverStatus) {
    if(request.live || request.profile!=Prefs(context).currentProfile ||
        !PrivateMedia.historyAllowed(request.source) || !PrivateMedia.historyAllowed(request.key) ||
        request.key.startsWith(DirectUsbIdentity.PREFIX)&&PrivateMedia.enabled)return
    val duration=status.duration.takeIf {it>0} ?: request.duration
    if(duration<=0)return
    val store=PositionStore(context,LocalActivity.scope(context,request.profile))
    if(status.finished)store.finished(request.key,duration)
    else store.put(request.key,status.position.coerceIn(0,duration),duration)
}

/** DLNA reports STOPPED both for completion and for the receiver's own Stop button. */
internal fun castProgressReport(request:CastRequest,status:ReceiverStatus,previous:ReceiverStatus?):ReceiverStatus {
    if(request.dlna==null || !status.finished)return status
    val duration=status.duration.takeIf {it>0} ?: previous?.duration?.takeIf {it>0} ?: request.duration
    val position=status.position.takeIf {it>0} ?: previous?.position ?: 0
    return status.copy(position=position,duration=duration,finished=duration>0 && position>=duration*.95)
}
