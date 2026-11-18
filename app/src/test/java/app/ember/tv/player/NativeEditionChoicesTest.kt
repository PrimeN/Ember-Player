package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class NativeEditionChoicesTest {
    @Test fun programsKeepTheirNativeOrderAndSelectedIdentity() {
        val names=listOf("News","Bitrate: 5 Mbps",null,"\u0000  ")
        val values=NativeEditions.read(4,1,{names[it]}){"Choice $it"}
        assertEquals(listOf("News","Bitrate: 5 Mbps","Choice 3","Choice 4"),values.map {it.title})
        assertEquals(listOf(1),values.filter {it.selected}.map {it.index})
        assertTrue(NativeEditions.read(-1,null,{error("No entries")}){"Unused"}.isEmpty())
        assertEquals(256,NativeEditions.read(Int.MAX_VALUE,null,{"Choice"}){"Unused"}.size)
    }
    @Test fun inPlaceProgramChangesDoNotWaitForAFileReload() {
        val restore=NativeEditionRestore()
        restore.request(42_000,true,false);restore.selectedInPlace()
        assertNull(restore.takeOnRestart())
        restore.request(42_000,true,false)
        assertEquals(42_000L,restore.takeOnRestart());assertNull(restore.takeOnRestart())
        restore.request(42_000,true,true);assertEquals(0L,restore.takeOnRestart())
    }
    @Test fun liveStreamsDoNotInheritAVodSeekAfterChangingProgram() {
        assertEquals(42_000L,NativeEditions.resumePosition(42_000,true,false))
        assertEquals(0L,NativeEditions.resumePosition(42_000,true,true))
        assertEquals(0L,NativeEditions.resumePosition(42_000,false,false))
    }
}
