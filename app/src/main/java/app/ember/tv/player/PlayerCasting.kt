package app.ember.tv.player

import android.content.Intent
import android.widget.Toast
import app.ember.player.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.openCasting() {
    if(!exports.permitted()||audioUrl!=null){Toast.makeText(this,"This source cannot be sent to a receiver.",Toast.LENGTH_LONG).show();return}
    val source=currentUrl ?: return
    val p=player ?: return
    val request=CastRequest(source,item.title,resumeKey,sourceHeaders,p.currentPosition,p.duration.coerceAtLeast(0),p.isCurrentMediaItemLive,MediaQueues.retain(queueId),profile=prefs.currentProfile)
    val id=CastRequests.stage(request)
    closeMenu();p.pause()
    try {startActivity(Intent(this,CastActivity::class.java).putExtra("request",id).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION))}
    catch(_:Exception){CastRequests.discard(id);Toast.makeText(this,"Could not open receiver controls.",Toast.LENGTH_LONG).show()}
}
