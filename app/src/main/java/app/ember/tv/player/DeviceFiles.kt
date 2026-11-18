package app.ember.tv.player

import app.ember.tv.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.ember.player.storage.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
internal data class DeviceFolder(val uri: String, val name: String)

internal data class DeviceFile(val uri: Uri, val parent: Uri, val name: String, val mime: String, val size: Long, val modified: Long, val flags: Int) {
    val directory get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
}

/** Folder access is explicit and uses persisted Android document grants. No startup scan. */
internal class DeviceFolders(private val readValue: () -> String?, private val writeValue: (String) -> Boolean) {
    constructor(context: Context) : this(
        { TokenStore(context.applicationContext).getForUpdate("player.folders") },
        { TokenStore(context.applicationContext).putEncrypted("player.folders", it) },
    )
    private fun load(): List<DeviceFolder> {
        val raw=readValue() ?: return emptyList()
        require(raw.length<=65536)
        val values=Json.decodeFromString<List<DeviceFolder>>(raw)
        require(values.size<=MAX_FOLDERS && values.all(::valid) && values.distinctBy {it.uri}.size==values.size)
        return values
    }
    fun readForDisplay():List<DeviceFolder> = load()
    fun read(): List<DeviceFolder> = runCatching {load()}.getOrDefault(emptyList())
    fun checkCapacity(uri:String)=synchronized(lock) {
        val values=load()
        if(values.none {it.uri==uri}&&values.size>=MAX_FOLDERS)throw SavedFolderLimitException()
    }
    fun addOrThrow(value:DeviceFolder)=synchronized(lock) {
        val values=load()
        require(valid(value))
        val updated=values.filterNot {it.uri==value.uri}+value
        if(updated.size>MAX_FOLDERS)throw SavedFolderLimitException()
        val encoded=Json.encodeToString(updated)
        check(encoded.length<=65536&&writeValue(encoded)) {"Could not save encrypted folders."}
    }
    fun add(value: DeviceFolder): Boolean = runCatching {addOrThrow(value);true}.getOrDefault(false)
    fun resetUnreadable():Boolean=synchronized(lock) {
        app.ember.player.storage.resetUnreadableList(::load){writeValue("[]")}
    }
    fun remove(uri: String): Boolean = update {values->values.filterNot {it.uri==uri}}
    private fun update(change:(List<DeviceFolder>)->List<DeviceFolder>):Boolean=synchronized(lock) {
        runCatching {
            val encoded=Json.encodeToString(change(load()))
            encoded.length<=65536 && writeValue(encoded)
        }.getOrDefault(false)
    }
    companion object {
        const val MAX_FOLDERS=16
        private val lock=Any()
        private fun valid(f:DeviceFolder)=f.name.isNotBlank() && f.name.length<=180 && f.uri.length<=4096 && runCatching {
            val uri=Uri.parse(f.uri)
            uri.scheme=="content" && !uri.authority.isNullOrBlank() && DocumentsContract.isTreeUri(uri)
        }.getOrDefault(false)
    }
}

internal class SavedFolderLimitException:IllegalStateException()

/** Failed imports relinquish only newly acquired access, including a newly added write grant. */
internal fun saveDeviceFolder(context:Context,tree:Uri,store:DeviceFolders=DeviceFolders(context),check:()->Unit={}):DeviceFolder {
    check();store.checkCapacity(tree.toString())
    val resolver=context.contentResolver
    fun flags()=resolver.persistedUriPermissions.filter {it.uri==tree}.fold(0) {value,permission->
        value or (if(permission.isReadPermission)Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if(permission.isWritePermission)Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    }
    val previous=flags()
    var saved=false
    try {
        DeviceFiles.retain(context,tree)
        check()
        val value=DeviceFolder(tree.toString(),DeviceFiles.name(context,DeviceFiles.root(tree)))
        check();store.addOrThrow(value);saved=true
        return value
    } finally {
        if(!saved) {
            val added=flags() and previous.inv()
            if(added!=0)resolver.releasePersistableUriPermission(tree,added)
        }
    }
}

internal object DeviceFiles {
    const val LIMIT = 200
    fun media(name: String, mime: String) = MediaFileFormats.media(name, mime)
    fun validName(name: String) = name.isNotBlank() && name == name.trim() && name.length <= 180 && name !in setOf(".", "..") && name.none { it == '/' || it == '\\' || it.isISOControl() }
    fun root(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    fun retain(context: Context, uri: Uri) {
        try {context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
        catch (_:SecurityException) {context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    }
    fun name(context: Context, uri: Uri): String = context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), android.os.Bundle.EMPTY, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.take(180) ?: context.resources.getString(R.string.ui_folder_30baa2)
    data class Page(val entries: List<DeviceFile>, val limited: Boolean, val nextOffset:Int=0)
    fun list(context: Context, tree: Uri, directory: Uri, offset:Int=0, check: () -> Unit = {}): Page {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(directory))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS)
        val entries = mutableListOf<DeviceFile>()
        require(offset>=0)
        val args=android.os.Bundle().apply {
            putInt(android.content.ContentResolver.QUERY_ARG_OFFSET,offset)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT,LIMIT+1)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION,android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
        }
        context.contentResolver.query(children, columns, args, null)?.use { cursor ->
            val honored=cursor.extras.getStringArray(android.content.ContentResolver.EXTRA_HONORED_ARGS).orEmpty().toSet()
            if(android.content.ContentResolver.QUERY_ARG_OFFSET !in honored && offset>0) {
                if(!cursor.moveToPosition(offset-1))return Page(emptyList(),false,offset)
            }
            var scanned=0
            while (cursor.moveToNext()) {
                check()
                if(scanned>=LIMIT)return Page(entries,true,offset+scanned)
                scanned++
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1)?.take(180) ?: continue
                val mime = cursor.getString(2) ?: "application/octet-stream"
                if (mime != DocumentsContract.Document.MIME_TYPE_DIR && !media(name, mime)) continue
                entries += DeviceFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), directory, name, mime,
                    if (cursor.isNull(3)) -1 else cursor.getLong(3), if (cursor.isNull(4)) 0 else cursor.getLong(4), cursor.getInt(5))
            }
        } ?: throw IOException("This folder is unavailable. Choose it again to renew access.")
        return Page(entries, false)
    }
    fun sorted(entries: List<DeviceFile>, query: String, sort: String, hidden: Boolean): List<DeviceFile> {
        val filtered = entries.filter { (hidden || !it.name.startsWith('.')) && it.name.contains(query, true) }
        val comparator:Comparator<DeviceFile> = when (sort) { "date" -> compareByDescending { it.modified }; "size" -> compareByDescending { it.size }; else -> compareBy(NaturalOrder) { it.name } }
        return filtered.sortedWith(compareByDescending<DeviceFile> { it.directory }.then(comparator).thenBy(NaturalOrder) { it.name })
    }
    fun rename(context: Context, file: DeviceFile, name: String): Uri {
        require(validName(name)) { "Enter a filename without slashes or control characters." }
        require(!file.directory) { "Select a file." }
        return DocumentsContract.renameDocument(context.contentResolver, file.uri, name) ?: throw IOException("This provider could not rename the file.")
    }
    fun delete(context: Context, files: List<DeviceFile>) {
        require(files.isNotEmpty() && files.none { it.directory })
        files.forEach { check(DocumentsContract.deleteDocument(context.contentResolver, it.uri)) { "Some files could not be deleted. Refresh the folder to see what remains." } }
    }
    /** A completed copy is closed and length-checked before the original is removed. */
    fun move(context: Context, file: DeviceFile, tree: Uri, check: () -> Unit = {}): Uri {
        require(!file.directory)
        val resolver = context.contentResolver
        val destination = root(tree)
        if (file.uri.authority == tree.authority && file.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0) {
            DocumentsContract.moveDocument(resolver, file.uri, file.parent, destination)?.let { return it }
        }
        val created = DocumentsContract.createDocument(resolver, destination, file.mime, file.name) ?: throw IOException("Cannot create a file in that folder.")
        var copied = false
        try {
            var total = 0L
            resolver.openInputStream(file.uri)?.use { input ->
                resolver.openOutputStream(created, "w")?.use { output ->
                    val bytes = ByteArray(64 * 1024)
                    while (true) { check(); val count = input.read(bytes); if (count < 0) break; output.write(bytes, 0, count); total += count }
                    output.flush()
                } ?: throw IOException("Cannot write the destination file.")
            } ?: throw IOException("Cannot read the source file.")
            kotlin.check(file.size < 0 || total == file.size) { "The file changed during the move. The original was kept." }
            check(); copied = true
            kotlin.check(DocumentsContract.deleteDocument(resolver, file.uri)) { "The copy succeeded, but the original could not be removed." }
            return created
        } finally { if (!copied) runCatching { DocumentsContract.deleteDocument(resolver, created) } }
    }
}
