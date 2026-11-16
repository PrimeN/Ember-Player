package app.ember.player

internal data class PlayerSettingSection(val id:String,val category:String,val title:String,val keywords:String) {
    fun matches(query:String):Boolean {val words=query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank);val text=(title+" "+keywords).lowercase();return words.all {it in text}}
}
internal object PlayerSettingsIndex {
    val sections=listOf(
        PlayerSettingSection("playback","playback","Playback","player interface glass ember basic streaming controls engine resume seeking buffer decoder memory picture in picture background audio exit IntroDB skip markers"),
        PlayerSettingSection("network","network","Network and buffering","connect connection timeout read forward rewind memory custom buffering cache read ahead refill"),
        PlayerSettingSection("sleep","playback","Sleep timer","sleep timer stop after end item"),
        PlayerSettingSection("enhancement","picture","Picture enhancement","GPU upscaling fast shader brightness contrast saturation gamma hue sharpness preset reset"),
        PlayerSettingSection("profiles","picture","Saved picture / shader profiles","saved picture shader profile name save delete"),
        PlayerSettingSection("hdr","picture","HDR output","HDR SDR tone mapping gamut peak scene analysis renderer stable advanced expansion"),
        PlayerSettingSection("decoding","picture","Picture","software hardware video decoder Hi10P frame rate resolution Dolby Vision deinterlacing"),
        PlayerSettingSection("touch","playback","Touch and rotation","gestures rotation double tap brightness volume pinch zoom temporary hold speed subtitle touch orientation"),
        PlayerSettingSection("audio","audio","Sound","sound audio night listening compression HDMI passthrough decoding track language lip sync delay Bluetooth equalizer preset bass treble stereo width channels"),
        PlayerSettingSection("subtitles","subtitles","Subtitles","captions subtitles language size background authored system translate translation offline voice speech read aloud timing font import colors outline spacing encoding saved styles providers"),
        PlayerSettingSection("appearance","appearance","Appearance and performance","appearance performance colors colours display mode shadow charcoal modern cinema red theme day night glass surfaces"),
        PlayerSettingSection("privacy","privacy","Privacy","privacy PIN private media hidden history recent activity"),
        PlayerSettingSection("backups","privacy","Backups","backup export import restore password encrypted settings"),
        PlayerSettingSection("about","about","About","about version licenses legal terms source credits"),
        PlayerSettingSection("cache","network","Cache","cache management disposable clear image artwork thumbnails temporary data"),
    )
    fun search(query:String)=sections.filter {it.matches(query)}
}
