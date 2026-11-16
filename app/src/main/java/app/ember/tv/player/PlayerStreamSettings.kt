package app.ember.tv.player

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.editStreamSettings() {
    fun store(value:PlaybackConfig){config=value;prefs.playbackConfig=config
        val p=value.stream.normalized();httpFactory.setConnectTimeoutMs(p.connectSeconds*1000).setReadTimeoutMs(p.readSeconds*1000)
        playbackEngine?.compatibility?.buffering(config);changed();editStreamSettings()}
    val p=config.stream.normalized()
    val options=mutableListOf(PlayerDialogAction("Buffer preset · ${config.buffer}") {
        showPlayerActions("Buffer preset",listOf("normal","large","huge").map {value->PlayerDialogAction(value.replaceFirstChar(Char::uppercase),selected=config.buffer==value&&!p.custom){store(config.copy(buffer=value,stream=p.copy(custom=false)))}})
    },PlayerDialogAction("Custom buffering · ${if(p.custom)"On" else "Off"}"){store(config.copy(stream=p.copy(custom=!p.custom)))})
    (timeoutFields+if(p.custom)bufferFields else emptyList()).forEach {f->options+=PlayerDialogAction("${f.label} · ${f.read(p)} ${f.unit}") {
        showPlayerActions(f.label,f.choices(p).map {v->PlayerDialogAction("$v ${f.unit}",selected=v==f.read(p)){store(config.copy(stream=f.write(p,v).normalized()))}})
    }}
    if(p.custom)options+=PlayerDialogAction("Wait for refill · ${if(p.pauseForBuffer)"On" else "Off"}"){store(config.copy(stream=p.copy(pauseForBuffer=!p.pauseForBuffer)))}
    val plan=StreamConfig.plan(this,config)
    showPlayerActions("Network and buffering",options,message="Device cap: ${plan.forwardMiB}+${plan.rewindMiB} MiB. Timeouts apply on reopen; Standard buffers update on prepare. Rewind memory is Compatibility-only.")
}
