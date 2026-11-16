package app.ember.tv.player

import java.math.BigDecimal

/** Entered positions are absolute; time syntax never changes with the device locale. */
internal object PlaybackControlInput {
    fun position(text:String):Long? = runCatching {
        val parts=text.trim().replace(',', '.').split(':')
        require(parts.size in 1..3 && text.length<=32)
        require(parts.dropLast(1).all {it.matches(Regex("[0-9]{1,9}"))})
        require(parts.last().matches(Regex("[0-9]{1,9}(\\.[0-9]{1,3})?")))
        val values=parts.map(::BigDecimal)
        require(values.drop(1).all {it<BigDecimal(60)})
        values.fold(BigDecimal.ZERO) {sum,value->sum*BigDecimal(60)+value}.movePointRight(3).longValueExact()
    }.getOrNull()

    fun speed(text:String):Float? = text.trim().replace(',', '.').takeIf {
        it.length<=8 && it.matches(Regex("[0-9]+(\\.[0-9]{1,3})?"))
    }?.toFloatOrNull()?.takeIf {it.isFinite() && it in .25f..4f}
}

internal data class SubtitleTouchState(val offsetMs:Long,val scalePercent:Int,val bottomMarginPercent:Int)

internal object SubtitleTouchMath {
    fun timing(initial:Long,fraction:Float):Long = if(!fraction.isFinite())initial else
        (initial.coerceIn(-60_000,60_000)+(fraction.coerceIn(-1f,1f)*10_000).toLong()).coerceIn(-60_000,60_000)
    fun position(initial:Int,fraction:Float):Int = if(!fraction.isFinite())initial.coerceIn(0,35) else
        (initial+fraction.coerceIn(-1f,1f)*100).toInt().coerceIn(0,35)
    fun scale(initial:Int,factor:Float):Int = if(!factor.isFinite() || factor<=0)initial.coerceIn(50,200) else
        (initial*factor).toInt().coerceIn(50,200)
}
