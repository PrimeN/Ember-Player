package app.ember.tv.player

import app.ember.tv.R

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.editStreamSettings() {
    fun store(value:PlaybackConfig){config=value;prefs.playbackConfig=config
        val p=value.stream.normalized();httpFactory.setConnectTimeoutMs(p.connectSeconds*1000).setReadTimeoutMs(p.readSeconds*1000)
        playbackEngine?.compatibility?.buffering(config);changed();editStreamSettings()}
    val p=config.stream.normalized()
    val presets=listOf("normal" to R.string.ui_normal,"large" to R.string.ui_large,"huge" to R.string.ui_huge)
    val presetName=resources.getString(presets.firstOrNull {it.first==config.buffer&&!p.custom}?.second ?: R.string.ui_custom)
    val options=mutableListOf(PlayerDialogAction(resources.getString(R.string.ui_buffer_preset ,presetName)) {
        showPlayerActions(resources.getString(R.string.ui_buffer_preset_7a2060),presets.map {(value,label)->PlayerDialogAction(resources.getString(label),selected=config.buffer==value&&!p.custom){store(config.copy(buffer=value,stream=p.copy(custom=false)))}})
    },PlayerDialogAction(resources.getString(R.string.ui_custom_buffering_7e833c ,if(p.custom)resources.getString(R.string.ui_on) else resources.getString(R.string.ui_off))){store(config.copy(stream=p.copy(custom=!p.custom)))})
    (timeoutFields+if(p.custom)bufferFields else emptyList()).forEach {f->options+=PlayerDialogAction("${resources.getString(f.label)} · ${f.read(p)} ${f.unit}") {
        showPlayerActions(resources.getString(f.label),f.choices(p).map {v->PlayerDialogAction("$v ${f.unit}",selected=v==f.read(p)){store(config.copy(stream=f.write(p,v).normalized()))}})
    }}
    if(p.custom)options+=PlayerDialogAction(resources.getString(R.string.ui_wait_for_refill ,if(p.pauseForBuffer)resources.getString(R.string.ui_on) else resources.getString(R.string.ui_off))){store(config.copy(stream=p.copy(pauseForBuffer=!p.pauseForBuffer)))}
    val plan=StreamConfig.plan(this,config)
    showPlayerActions(resources.getString(R.string.ui_network_and_buffering),options,message=resources.getString(R.string.ui_device_cap_mib_timeouts_apply_on_reopen_standard_buffers ,plan.forwardMiB.toString(),plan.rewindMiB.toString()))
}
