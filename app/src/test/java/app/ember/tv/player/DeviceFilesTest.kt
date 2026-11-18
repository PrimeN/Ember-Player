package app.ember.tv.player

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.InterruptedIOException

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class DeviceFilesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: DeviceTestProvider
    private val tree = Uri.parse("content://device.files/tree/root")
    @Before fun setup() {
        provider = DeviceTestProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "device.files"; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        ShadowContentResolver.registerProviderInternal("device.files", provider)
    }
    private fun source() = DeviceFiles.list(context, tree, DeviceFiles.root(tree)).entries.first {it.name == "video.mkv"}

    @Test fun listingFiltersNonMediaAndSortingKeepsFoldersFirst() {
        val entries = DeviceFiles.list(context, tree, DeviceFiles.root(tree)).entries
        assertEquals(setOf("video.mkv", "Subfolder", ".hidden.mp4"), entries.map {it.name}.toSet())
        assertEquals(listOf("Subfolder", "video.mkv"), DeviceFiles.sorted(entries, "", "name", false).map {it.name})
        assertEquals(listOf(".hidden.mp4"), DeviceFiles.sorted(entries, "HIDDEN", "size", true).map {it.name})
    }
    @Test fun folderSearchAndNaturalSortIncludeFilesBeyondTheProviderPage() {
        val files=(1..450).reversed().map {n->DeviceFile(Uri.parse("content://files/$n"),tree,"Episode $n.mp4","video/mp4",n.toLong(),n.toLong(),0)}
        fun load(offset:Int)=DeviceFiles.Page(files.drop(offset).take(200),offset+200<files.size,offset+200)
        val first=DeviceFiles.browsePages(::load,"","name",false)
        assertEquals((1..200).map {"Episode $it.mp4"},first.entries.map {it.name})
        val second=DeviceFiles.browsePages(::load,"","name",false,first.next)
        assertEquals((201..400).map {"Episode $it.mp4"},second.entries.map {it.name})
        assertTrue(first.entries.map {it.uri}.intersect(second.entries.map {it.uri}.toSet()).isEmpty())
        assertEquals(listOf("Episode 2.mp4","Episode 20.mp4","Episode 21.mp4"),DeviceFiles.browsePages(::load,"episode 2","name",false).entries.take(3).map {it.name})
        assertEquals("Episode 450.mp4",DeviceFiles.browsePages(::load,"","date",false).entries.first().name)
        assertEquals("Episode 450.mp4",DeviceFiles.browsePages(::load,"","size",false).entries.first().name)
        assertFalse(DeviceFiles.browsePages(::load,"","name",false,hiddenMedia={it.endsWith("/2")}).entries.any {it.uri.toString().endsWith("/2")})
        var checked=0
        assertTrue(runCatching {DeviceFiles.browsePages(::load,"","name",false,check={if(++checked>10)throw kotlinx.coroutines.CancellationException()})}.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
    @Test fun providersThatHonorLimitsWithoutOffsetsDoNotTruncateLaterPages() {
        provider.honorsOnlyLimit=true;provider.populateMedia(450)
        val second=DeviceFiles.list(context,tree,DeviceFiles.root(tree),200)
        assertEquals(200,second.entries.size);assertEquals("Episode 200.mp4",second.entries.first().name);assertTrue(second.limited)
        val last=DeviceFiles.list(context,tree,DeviceFiles.root(tree),400)
        assertEquals(50,last.entries.size);assertFalse(last.limited)
    }
    @Test fun renameReturnsTheNewAddressAndRejectsTraversal() {
        assertEquals("renamed.mp4", DeviceFiles.name(context, DeviceFiles.rename(context, source(), "renamed.mp4")))
        assertFalse(DeviceFiles.validName("../video.mkv"))
        assertFalse(DeviceFiles.validName("bad\u0000name"))
        assertFalse(DeviceFiles.validName("."))
    }
    @Test fun successfulMoveCopiesBytesBeforeDeletingTheOriginal() {
        val source = source()
        val original = provider.files.getValue("video").readBytes()
        val moved = DeviceFiles.move(context, source, Uri.parse("content://device.files/tree/destination"))
        assertFalse(provider.files.containsKey("video"))
        context.contentResolver.openInputStream(moved)!!.use {assertArrayEquals(original, it.readBytes())}
    }
    @Test fun cancelledMoveKeepsOriginalAndRemovesPartialDestination() {
        val original = source()
        val initial = provider.files.keys.toSet()
        var checks = 0
        try {
            DeviceFiles.move(context, original, Uri.parse("content://device.files/tree/destination")) {
                if (++checks >= 2) throw InterruptedIOException("Cancelled")
            }
            fail("Expected cancellation")
        } catch (_: InterruptedIOException) {}
        assertEquals(initial, provider.files.keys.toSet())
        assertEquals(100000L, provider.files.getValue("video").length())
    }
    @Test fun encryptedFolderStorePreservesCorruptDataUntilItCanBeRead() {
        var raw: String? = "not json"
        val store = DeviceFolders({raw}, {raw = it; true})
        assertTrue(store.read().isEmpty())
        assertFalse(store.add(DeviceFolder(tree.toString(), "Videos")))
        assertFalse(store.remove(tree.toString()))
        assertEquals("not json",raw)
        raw=null
        assertTrue(store.add(DeviceFolder(tree.toString(), "Videos")))
        assertTrue(store.add(DeviceFolder(tree.toString(), "Renamed")))
        assertEquals(listOf(DeviceFolder(tree.toString(), "Renamed")), store.read())
        assertTrue(store.remove(tree.toString()))
        assertTrue(store.read().isEmpty())
    }
    @Test fun folderMutationsRejectUnreadablePartialAndOversizedCopiesWithoutWriting() {
        val valid=DeviceFolder(tree.toString(),"Videos")
        for(raw in listOf("not json", " ".repeat(65537),Json.encodeToString(listOf(valid,valid.copy(uri="file:///private"))),Json.encodeToString(listOf(valid,valid)))) {
            var writes=0
            val store=DeviceFolders({raw},{writes++;true})
            assertFalse(store.add(valid));assertFalse(store.remove(valid.uri));assertEquals(0,writes)
        }
        var writes=0
        val unavailable=DeviceFolders({throw IllegalStateException("Locked")},{writes++;true})
        assertFalse(unavailable.add(valid));assertFalse(unavailable.remove(valid.uri));assertEquals(0,writes)
    }
    @Test fun folderLimitAndWriteFailureDoNotDiscardExistingRoots() {
        var raw=Json.encodeToString((1..16).map {DeviceFolder("content://device.files/tree/$it","Folder $it")})
        val original=raw
        val store=DeviceFolders({raw},{raw=it;true})
        assertFalse(store.add(DeviceFolder(tree.toString(),"Extra")))
        assertEquals(original,raw)
        val failed=DeviceFolders({raw},{false})
        assertFalse(failed.remove("content://device.files/tree/1"));assertEquals(original,raw)
        assertTrue(store.add(DeviceFolder("content://device.files/tree/1","Renamed")))
        assertEquals(16,store.read().size)
    }

}

class DeviceTestProvider : DocumentsProvider() {
    val files = linkedMapOf<String, File>()
    var failQueries=false
    var honorsOnlyLimit=false
    fun populateMedia(count:Int){children=(0 until count).map {n->"item-$n".also {names[it]="Episode $n.mp4"}}}
    var children=listOf("video", "folder", "hidden", "other")
    private val names = mutableMapOf("root" to "Root", "destination" to "Destination", "folder" to "Subfolder", "video" to "video.mkv", "hidden" to ".hidden.mp4", "other" to "notes.txt")
    override fun onCreate(): Boolean {
        files["video"] = File.createTempFile("device-video", ".tmp", context!!.cacheDir).apply {writeBytes(ByteArray(100000) {it.toByte()})}
        files["hidden"] = File.createTempFile("device-hidden", ".tmp", context!!.cacheDir).apply {writeText("hidden")}
        return true
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId in names
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id"))
    private fun cursor(ids: List<String>, projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS)
        return MatrixCursor(columns).apply {ids.forEach {id ->
            addRow(columns.map {column -> when(column) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> names[id]
                DocumentsContract.Document.COLUMN_MIME_TYPE -> if (id in setOf("root", "destination", "folder")) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                DocumentsContract.Document.COLUMN_SIZE -> files[id]?.length() ?: 0L
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1L
                DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                else -> null
            }})
        }}
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?):Cursor {
        if(failQueries)throw IllegalStateException("Metadata unavailable")
        return cursor(listOf(documentId),projection)
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = cursor(children, projection)
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,queryArgs:android.os.Bundle?):Cursor {
        if(!honorsOnlyLimit)return cursor(children,projection)
        return cursor(children.take(queryArgs?.getInt(android.content.ContentResolver.QUERY_ARG_LIMIT,Int.MAX_VALUE) ?: Int.MAX_VALUE),projection).apply {
            extras=android.os.Bundle().apply {putStringArray(android.content.ContentResolver.EXTRA_HONORED_ARGS,arrayOf(android.content.ContentResolver.QUERY_ARG_LIMIT))}
        }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = ParcelFileDescriptor.open(files.getValue(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val id = "created-${files.size}"
        files[id] = File.createTempFile("device-copy", ".tmp", context!!.cacheDir); names[id] = displayName
        return id
    }
    override fun renameDocument(documentId: String, displayName: String): String {names[documentId] = displayName; return documentId}
    override fun deleteDocument(documentId: String) {files.remove(documentId)?.delete(); names.remove(documentId)}
}
