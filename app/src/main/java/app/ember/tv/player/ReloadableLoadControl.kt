package app.ember.tv.player

import androidx.media3.common.C
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator

/** One player owns this allocator. Reopening a source latches new settings on its playback thread. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ReloadableLoadControl(initial:PlaybackConfig,private val read:()->PlaybackConfig,private val plan:(PlaybackConfig)->StreamPlan):LoadControl {
    private val allocator=DefaultAllocator(true,C.DEFAULT_BUFFER_SEGMENT_SIZE)
    private var current=plan(initial)
    private var minimumUs=minimum(initial,current)
    private var loading=false
    private fun minimum(config:PlaybackConfig,p:StreamPlan)=minOf(if(config.stream.custom)p.readAheadSeconds.coerceAtLeast(1) else if(config.buffer=="normal")30 else 50,p.aheadSeconds)*1_000_000L
    private fun target()=current.forwardMiB*1024*1024
    init {allocator.setTargetBufferSize(target())}
    override fun onPrepared(playerId:PlayerId){val config=read();current=plan(config);minimumUs=minimum(config,current);loading=false;allocator.setTargetBufferSize(target())}
    override fun onTracksSelected(parameters:LoadControl.Parameters,trackGroups:TrackGroupArray,trackSelections:Array<out ExoTrackSelection?>){}
    override fun onStopped(playerId:PlayerId){loading=false;allocator.reset()}
    override fun onReleased(playerId:PlayerId){loading=false;allocator.reset()}
    override fun getAllocator(playerId:PlayerId)=allocator
    override fun getBackBufferDurationUs(playerId:PlayerId)=0L
    override fun retainBackBufferFromKeyframe(playerId:PlayerId)=false
    override fun shouldContinueLoading(parameters:LoadControl.Parameters):Boolean {
        val min=(minimumUs*parameters.playbackSpeed.coerceAtLeast(1f)).toLong().coerceAtMost(current.aheadSeconds*1_000_000L)
        loading=when {
            allocator.totalBytesAllocated>=target()->false
            parameters.bufferedDurationUs>=current.aheadSeconds*1_000_000L->false
            parameters.bufferedDurationUs<min->true
            else->loading
        }
        return loading
    }
    override fun shouldStartPlayback(parameters:LoadControl.Parameters):Boolean {
        var required=if(parameters.rebuffering){if(current.pauseForBuffer)current.refillSeconds*1_000_000L else 0L} else 2_500_000L
        if(parameters.targetLiveOffsetUs!=C.TIME_UNSET)required=minOf(required,parameters.targetLiveOffsetUs/2)
        return parameters.bufferedDurationUs>=(required*parameters.playbackSpeed).toLong() ||
            parameters.bufferedDurationUs>0 && allocator.totalBytesAllocated>=target()
    }
}
