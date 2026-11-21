package app.ember.tv.player

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class FolderBrowseVisitTest {
    private val parent=Uri.parse("content://folder/root")
    private fun files()=(1..1000).reversed().map {n->DeviceFile(Uri.parse("content://folder/$n"),parent,"Episode $n.mp4","video/mp4",n.toLong(),n.toLong(),0)}
    @Test fun pagingAndChangingSortOrSearchReuseOneProviderScan() {
        val entries=files();var reads=0
        fun load(offset:Int):DeviceFiles.Page {reads++;return DeviceFiles.Page(entries.drop(offset).take(200),offset+200<entries.size,offset+200)}
        val visit=FolderBrowseVisit()
        val first=visit.page(::load,"","name",false)
        val scans=reads
        assertEquals(5,scans)
        val second=visit.page(::load,"","name",false,first.next)
        assertEquals((201..400).map {"Episode $it.mp4"},second.entries.map {it.name})
        assertEquals("Episode 1000.mp4",visit.page(::load,"","size",false).entries.first().name)
        assertEquals("Episode 20.mp4",visit.page(::load,"episode 20","name",false).entries.first().name)
        assertEquals(scans,reads)
        assertFalse(visit.page(::load,"","name",false,hiddenMedia={it.endsWith("/2")}).entries.any {it.uri.toString().endsWith("/2")})
        visit.close()
        assertTrue(runCatching {visit.page(::load,"","name",false)}.exceptionOrNull() is CancellationException)
        assertEquals(scans,reads)
    }
    @Test fun oversizedVisitsKeepTheBoundedFallbackWithoutTruncatingResults() {
        val entries=files();var reads=0
        fun load(offset:Int):DeviceFiles.Page {reads++;return DeviceFiles.Page(entries.drop(offset).take(200),offset+200<entries.size,offset+200)}
        val visit=FolderBrowseVisit(maxEntries=20)
        val first=visit.page(::load,"","name",false)
        assertEquals(5,reads)
        val second=visit.page(::load,"","name",false,first.next)
        assertEquals(10,reads)
        assertEquals("Episode 201.mp4",second.entries.first().name)
    }
    @Test fun endingAVisitDuringAnOutstandingReadCannotRestoreItsSnapshot() {
        val visit=FolderBrowseVisit();var reads=0
        fun load(offset:Int):DeviceFiles.Page {reads++;visit.close();return DeviceFiles.Page(files(),false)}
        assertTrue(runCatching {visit.page(::load,"","name",false)}.exceptionOrNull() is CancellationException)
        assertTrue(runCatching {visit.page(::load,"","name",false)}.exceptionOrNull() is CancellationException)
        assertEquals(1,reads)
    }
    @Test fun cancelledScansCannotPublishAPartialSnapshot() {
        val entries=files();val visit=FolderBrowseVisit();var stop=true;var reads=0
        fun load(offset:Int):DeviceFiles.Page {reads++;return DeviceFiles.Page(entries.drop(offset).take(200),offset+200<entries.size,offset+200)}
        assertTrue(runCatching {visit.page(::load,"","name",false,check={if(stop&&reads>=2)throw CancellationException()})}.isFailure)
        stop=false
        val first=visit.page(::load,"","name",false)
        assertEquals("Episode 1.mp4",first.entries.first().name)
        assertEquals("Episode 1000.mp4",visit.page(::load,"","size",false).entries.first().name)
    }
}
