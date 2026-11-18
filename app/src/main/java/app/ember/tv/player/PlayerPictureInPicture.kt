package app.ember.tv.player

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.media3.common.VideoSize
import kotlin.math.roundToInt

internal fun pictureInPictureRatio(size:VideoSize?):Rational {
    val ratio=if(size!=null && size.width>0 && size.height>0)
        size.width.toDouble()*size.pixelWidthHeightRatio/size.height else 16.0/9
    val bounded=if(ratio.isFinite() && ratio>0)ratio.coerceIn(.42,2.38) else 16.0/9
    return Rational((bounded*10000).roundToInt(),10000)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.pictureInPictureParams():PictureInPictureParams {
    val builder=PictureInPictureParams.Builder().setAspectRatio(pictureInPictureRatio(player?.videoSize))
    if(Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(pictureInPictureAllowed())
    val rect=Rect()
    if(viewInitialized && (view.videoSurfaceView ?: view).getGlobalVisibleRect(rect) && !rect.isEmpty)builder.setSourceRectHint(rect)
    return builder.build()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.pictureInPictureAllowed()=configInitialized && config.pip && !privatePlayback && !pickingSidecar &&
    player?.isPlaying==true && !isFinishing && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.updatePictureInPicture() {
    if(packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE))
        runCatching {setPictureInPictureParams(pictureInPictureParams())}
}
