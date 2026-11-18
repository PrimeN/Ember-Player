package app.ember.player

import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun filenameSuggestionsKeepTheDecodedPathExtensionAndExcludeUrlParameters() {
        assertEquals("Movie One.mkv",DownloadStore.suggestedName("https://example.test/Movies/Movie%20One.mkv?token=secret"))
        assertEquals("clip.webm",DownloadStore.suggestedName("https://example.test/clip.webm#ignored"))
        assertEquals("AB.webm",DownloadStore.suggestedName("https://example.test/A%2BB.webm"))
        assertEquals("video.mkv",DownloadStore.suggestedName("https://example.test/a%2Fvideo.mkv"))
        assertEquals("watch.mp4",DownloadStore.suggestedName("https://example.test/watch?name=clip"))
        for(url in listOf("https://example.test/","https://example.test/?name=video.mp4","not a URL"))assertEquals("media.mp4",DownloadStore.suggestedName(url))
    }
    @Test fun downloadAcceptsDirectFilesAndRejectsManifestsOrLocalSchemes() {
        assertTrue(DownloadStore.supported("https://example.test/movie.mp4?token=value"))
        for(url in listOf("file:///data/private","content://provider/file","ftp://example.test/movie.mp4","https://example.test/live.m3u8","https://example.test/stream.mpd","https://example.test/movie.mp4#fragment"))assertFalse(url,DownloadStore.supported(url))
    }
    @Test fun filenamesCannotEscapeTheDownloadFolder() {
        assertEquals("movie.mp4",DownloadStore.safeName("../../movie.mp4"))
        assertEquals("movie.mkv",DownloadStore.safeName("folder\\movie.mkv"))
        assertEquals("media.mp4",DownloadStore.safeName(".."))
        assertFalse(DownloadStore.safeName("bad\u0000name.mp4").contains('\u0000'))
    }
    @Test fun resumingRequiresAnExactRangeThroughTheEndOfTheFile() {
        assertTrue(MediaDownloadService.validPartial("bytes 100-999/1000",100))
        assertFalse(MediaDownloadService.validPartial("bytes 0-999/1000",100))
        assertFalse(MediaDownloadService.validPartial("bytes 100-500/1000",100))
        assertFalse(MediaDownloadService.validPartial("bytes 100-999/*",100))
        assertFalse(MediaDownloadService.validPartial("bytes 100-99/1000",100))
        assertFalse(MediaDownloadService.validPartial(null,100))
    }
}
