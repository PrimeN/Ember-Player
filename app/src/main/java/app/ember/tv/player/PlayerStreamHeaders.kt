package app.ember.tv.player

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerActivity.applyStreamHeaders(headers: Map<String, String>) {
    val agent = app.ember.tv.data.StreamHeaders.userAgent(headers) ?: app.ember.tv.data.HttpIdentity.userAgent
    val rest = app.ember.tv.data.StreamHeaders.withoutUserAgent(headers)
    sourceHeaders = rest + ("User-Agent" to agent)
    httpFactory.setUserAgent(agent)
    httpFactory.setDefaultRequestProperties(rest)
}
