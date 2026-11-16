package app.ember.tv.player

import android.content.Context
import android.net.Uri
import app.ember.player.PrivateMedia
import app.ember.tv.tracker.TokenStore
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.Closeable
import java.security.MessageDigest

/** Rebuildable index. Filenames, document grants and search terms never enter a cleartext database. */
internal class MediaLibrary(context:Context,
    open:(Context)->androidx.sqlite.db.SupportSQLiteDatabase= {MediaLibrary.encrypted(it)},
    private val folders:()->List<DeviceFolder> ={DeviceFolders(context).read()},
    private val hidden:(String)->Boolean=PrivateMedia::hidden,
):Closeable {
    private val app=context.applicationContext
    private val db=open(app)
    init {
        db.execSQL("CREATE TABLE IF NOT EXISTS media (id TEXT PRIMARY KEY, root TEXT NOT NULL, uri TEXT NOT NULL, title TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER, modified INTEGER, generation TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS media_root ON media(root)")
        db.execSQL("CREATE INDEX IF NOT EXISTS media_title ON media(title COLLATE NOCASE)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS names USING fts4(title, tokenize=unicode61)")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_insert AFTER INSERT ON media BEGIN INSERT INTO names(docid,title) VALUES(new.rowid,new.title); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_delete BEFORE DELETE ON media BEGIN DELETE FROM names WHERE docid=old.rowid; END")
        db.query("PRAGMA secure_delete=ON").use {it.moveToFirst()}
        db.query("PRAGMA busy_timeout=5000").use {it.moveToFirst()}
    }
    fun refresh(folders:List<DeviceFolder>,check:()->Unit,progress:(Int)->Unit) {
        val roots=folders.filterNot {hidden(it.uri)}
        val known=roots.map {digest(it.uri)}.toSet()
        val removed=mutableListOf<String>()
        db.query("SELECT DISTINCT root FROM media").use {while(it.moveToNext())if(it.getString(0) !in known)removed+=it.getString(0)}
        removed.forEach {db.execSQL("DELETE FROM media WHERE root=?",arrayOf(it))}
        var count=0
        for(folder in roots) {
            check()
            val generation=java.util.UUID.randomUUID().toString();val root=digest(folder.uri);val tree=Uri.parse(folder.uri)
            val pending=java.util.ArrayDeque<Uri>();pending.add(DeviceFiles.root(tree));val visited=hashSetOf<String>()
            while(pending.isNotEmpty()) {
                check();val directory=pending.removeFirst()
                if(!visited.add(directory.toString()))continue
                require(visited.size<=100000) {"This folder has too many subdirectories. Add a smaller root."}
                var offset=0
                do {
                    val page=DeviceFiles.list(app,tree,directory,offset,check)
                    db.beginTransaction()
                    try {
                        for(file in page.entries) {
                            check();if(hidden(file.uri.toString()))continue
                            if(file.directory)pending.add(file.uri)
                            else {
                                // DELETE first keeps the FTS index correct when a file is replaced.
                                db.execSQL("DELETE FROM media WHERE id=?",arrayOf(digest(file.uri.toString())))
                                db.execSQL("INSERT INTO media VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(digest(file.uri.toString()),root,file.uri.toString(),file.name,file.mime,file.size,file.modified,generation))
                                count++
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally {db.endTransaction()}
                    progress(count);offset=if(page.limited)page.nextOffset else -1
                } while(offset>=0)
            }
            check();db.execSQL("DELETE FROM media WHERE root=? AND generation<>?",arrayOf(root,generation))
        }
    }
    data class Result(val entries:List<QueueMedia>,val nextOffset:Int?,val modified:Map<String,Long> = emptyMap())
    fun search(query:String,offset:Int=0,sort:String="name"):Result {
        require(offset>=0)
        val allowed=folders().filterNot {hidden(it.uri)}.map {digest(it.uri)}.toSet()
        val match=terms(query)
        val found=mutableListOf<QueueMedia>();val dates=mutableMapOf<String,Long>();var cursorOffset=offset;var more=true
        val order=if(sort=="modified")"modified DESC,title COLLATE NOCASE,id" else "title COLLATE NOCASE,id"
        while(found.size<100 && more) {
            val clause=if(match.isEmpty())"" else " WHERE rowid IN (SELECT docid FROM names WHERE names MATCH ?)"
            val args=(if(match.isEmpty())emptyList() else listOf(match))+listOf("100",cursorOffset.toString())
            var rows=0
            db.query("SELECT uri,title,root,modified FROM media$clause ORDER BY $order LIMIT ? OFFSET ?",args.toTypedArray()).use {cursor->
                while(cursor.moveToNext()) {
                    rows++;cursorOffset++
                    if(cursor.getString(2) in allowed && !hidden(cursor.getString(0))) {
                        val media=QueueMedia(cursor.getString(0),cursor.getString(1));found+=media;dates[media.key]=cursor.getLong(3)
                    }
                    if(found.size==100)break
                }
            }
            more=rows==100
        }
        return Result(found,if(more)cursorOffset else null,dates)
    }
    override fun close(){db.close()}
    companion object {
        private fun encrypted(app:Context):androidx.sqlite.db.SupportSQLiteDatabase {
        System.loadLibrary("sqlcipher")
        val store=TokenStore(app)
        val secret=synchronized(keyLock) {
            store.get("player.library.key") ?: ByteArray(32).also(java.security.SecureRandom()::nextBytes)
                .joinToString(""){"%02x".format(it)}.also {check(store.putEncrypted("player.library.key",it)) {"Encrypted library storage is unavailable."}}
        }
        val file=app.getDatabasePath("media-library.db");file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file,secret,null,null,null)
        }
        private val keyLock=Any()
        fun terms(query:String)=Regex("[\\p{L}\\p{N}]+").findAll(query.take(120)).take(12).joinToString(" "){"\"${it.value}\"*"}
        private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    }
}
