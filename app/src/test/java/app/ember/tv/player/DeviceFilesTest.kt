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
    @Test fun encryptedFolderStoreRejectsCorruptDataAndDeduplicatesRoots() {
        var raw: String? = "not json"
        val store = DeviceFolders({raw}, {raw = it; true})
        assertTrue(store.read().isEmpty())
        assertTrue(store.add(DeviceFolder(tree.toString(), "Videos")))
        assertTrue(store.add(DeviceFolder(tree.toString(), "Renamed")))
        assertEquals(listOf(DeviceFolder(tree.toString(), "Renamed")), store.read())
        assertTrue(store.remove(tree.toString()))
        assertTrue(store.read().isEmpty())
    }
}

class DeviceTestProvider : DocumentsProvider() {
    val files = linkedMapOf<String, File>()
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
    override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(listOf(documentId), projection)
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = cursor(children, projection)
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = ParcelFileDescriptor.open(files.getValue(documentId), ParcelFileDescriptor.parseMode(mode))
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val id = "created-${files.size}"
        files[id] = File.createTempFile("device-copy", ".tmp", context!!.cacheDir); names[id] = displayName
        return id
    }
    override fun renameDocument(documentId: String, displayName: String): String {names[documentId] = displayName; return documentId}
    override fun deleteDocument(documentId: String) {files.remove(documentId)?.delete(); names.remove(documentId)}
}
