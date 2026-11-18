package app.ember.tv.player

import android.content.Context
import android.net.Uri
import app.ember.player.PrivateMedia
import app.ember.player.storage.TokenStore
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
        db.execSQL("CREATE TABLE IF NOT EXISTS media_order (id TEXT PRIMARY KEY, sort_key TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS media_natural_order ON media_order(sort_key,id)")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_order_delete AFTER DELETE ON media BEGIN DELETE FROM media_order WHERE id=old.id; END")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS names USING fts4(title, tokenize=unicode61)")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_insert AFTER INSERT ON media BEGIN INSERT INTO names(docid,title) VALUES(new.rowid,new.title); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS media_delete BEFORE DELETE ON media BEGIN DELETE FROM names WHERE docid=old.rowid; END")
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_session (id INTEGER PRIMARY KEY, signature TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_roots (root TEXT PRIMARY KEY, generation TEXT NOT NULL, complete INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, root TEXT NOT NULL, path TEXT NOT NULL, offset INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, UNIQUE(root,path))")
        db.execSQL("CREATE INDEX IF NOT EXISTS scan_remaining ON scan_queue(root,done,id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS search_schema (id INTEGER PRIMARY KEY, version INTEGER NOT NULL)")
        val searchVersion=db.query("SELECT version FROM search_schema WHERE id=1").use {if(it.moveToFirst())it.getInt(0) else 0}
        if(searchVersion!=1)transaction {
            // Existing display names are retained; missing order rows rebuild their normalized FTS terms in batches.
            db.execSQL("DELETE FROM media_order")
            db.execSQL("INSERT OR REPLACE INTO search_schema VALUES(1,1)")
        }
        db.query("PRAGMA secure_delete=ON").use {it.moveToFirst()}
        db.query("PRAGMA busy_timeout=5000").use {it.moveToFirst()}
    }
    fun refresh(folders:List<DeviceFolder>,check:()->Unit,progress:(Int)->Unit) {
        try {
            val roots=folders.filterNot {hidden(it.uri)}
            val signature=digest(roots.map {it.uri}.sorted().joinToString("\n")+PrivateMedia.libraryVisibility.toString())
            val previous=db.query("SELECT signature FROM scan_session WHERE id=1").use {if(it.moveToFirst())it.getString(0) else null}
            if(previous!=signature)transaction {
                db.execSQL("DELETE FROM scan_queue");db.execSQL("DELETE FROM scan_roots")
                db.execSQL("INSERT OR REPLACE INTO scan_session VALUES(1,?)",arrayOf(signature))
            }
            val known=roots.map {digest(it.uri)}.toSet()
            val removed=mutableListOf<String>()
            db.query("SELECT DISTINCT root FROM media").use {while(it.moveToNext())if(it.getString(0) !in known)removed+=it.getString(0)}
            removed.forEach {db.execSQL("DELETE FROM media WHERE root=?",arrayOf(it))}
            var count=0
            for(folder in roots) {
                check()
                val root=digest(folder.uri);val tree=Uri.parse(folder.uri)
                var complete=false
                val generation=db.query("SELECT generation,complete FROM scan_roots WHERE root=?",arrayOf(root)).use {
                    if(it.moveToFirst()){complete=it.getInt(1)!=0;it.getString(0)}else null
                } ?: java.util.UUID.randomUUID().toString().also {value->transaction {
                    db.execSQL("INSERT INTO scan_roots VALUES(?,?,0)",arrayOf(root,value))
                    db.execSQL("INSERT INTO scan_queue(root,path) VALUES(?,?)",arrayOf(root,DeviceFiles.root(tree).toString()))
                }}
                count+=db.query("SELECT COUNT(*) FROM media WHERE root=? AND generation=?",arrayOf(root,generation)).use {it.moveToFirst();it.getInt(0)}
                if(complete){progress(count);continue}
                var directories=db.query("SELECT COUNT(*) FROM scan_queue WHERE root=?",arrayOf(root)).use {it.moveToFirst();it.getInt(0)}
                while(true) {
                    check()
                    val pending=db.query("SELECT id,path,offset FROM scan_queue WHERE root=? AND done=0 ORDER BY id LIMIT 1",arrayOf(root)).use {
                        if(it.moveToFirst())Triple(it.getLong(0),it.getString(1),it.getInt(2)) else null
                    } ?: break
                    val page=DeviceFiles.list(app,tree,Uri.parse(pending.second),pending.third,check)
                    transaction {
                        for(file in page.entries) {
                            check();if(hidden(file.uri.toString()))continue
                            if(file.directory) {
                                val path=file.uri.toString()
                                val exists=db.query("SELECT 1 FROM scan_queue WHERE root=? AND path=?",arrayOf(root,path)).use {it.moveToFirst()}
                                if(!exists) {
                                    require(++directories<=100000) {"This folder has too many subdirectories. Add a smaller root."}
                                    db.execSQL("INSERT INTO scan_queue(root,path) VALUES(?,?)",arrayOf(root,path))
                                }
                            } else {
                                db.execSQL("DELETE FROM media WHERE id=?",arrayOf(digest(file.uri.toString())))
                                db.execSQL("INSERT INTO media VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(digest(file.uri.toString()),root,file.uri.toString(),file.name,file.mime,file.size,file.modified,generation))
                                count++
                            }
                        }
                        // Cursor and indexed rows commit together. A cancelled page is retried safely.
                        if(page.limited) {
                            require(page.nextOffset>pending.third)
                            db.execSQL("UPDATE scan_queue SET offset=? WHERE id=?",arrayOf<Any>(page.nextOffset,pending.first))
                        } else db.execSQL("UPDATE scan_queue SET done=1 WHERE id=?",arrayOf(pending.first))
                    }
                    progress(count)
                }
                check()
                transaction {
                    db.execSQL("DELETE FROM media WHERE root=? AND generation<>?",arrayOf(root,generation))
                    db.execSQL("UPDATE scan_roots SET complete=1 WHERE root=?",arrayOf(root))
                }
            }
            check()
            transaction {db.execSQL("DELETE FROM scan_queue");db.execSQL("DELETE FROM scan_roots");db.execSQL("DELETE FROM scan_session")}
        } finally {revision.incrementAndGet()}
    }
    private inline fun transaction(block:()->Unit) {
        db.beginTransaction()
        try {block();db.setTransactionSuccessful()}finally {db.endTransaction()}
    }
    data class Result(val entries:List<QueueMedia>,val nextOffset:Int?,val modified:Map<String,Long> = emptyMap())
    private fun updateNameOrder() {
        while(true) {
            val pending=mutableListOf<Pair<String,String>>()
            db.query("SELECT media.id,title FROM media LEFT JOIN media_order ON media.id=media_order.id WHERE media_order.id IS NULL LIMIT 500").use {cursor->
                while(cursor.moveToNext())pending+=cursor.getString(0) to cursor.getString(1)
            }
            if(pending.isEmpty())return
            db.beginTransaction()
            try {
                pending.forEach {(id,title)->
                    db.execSQL("INSERT OR REPLACE INTO media_order VALUES(?,?)",arrayOf(id,NaturalOrder.key(title)))
                    db.execSQL("UPDATE names SET title=? WHERE docid=(SELECT rowid FROM media WHERE id=?)",arrayOf(MediaSearch.tokens(title).joinToString(" "),id))
                }
                db.setTransactionSuccessful()
            } finally {db.endTransaction()}
        }
    }
    fun search(query:String,offset:Int=0,sort:String="name"):Result {
        require(offset>=0)
        updateNameOrder()
        val allowed=folders().filterNot {hidden(it.uri)}.map {digest(it.uri)}.toSet()
        val match=terms(query)
        val found=mutableListOf<QueueMedia>();val dates=mutableMapOf<String,Long>();var cursorOffset=offset;var more=true
        val order=(if(sort=="modified")"modified DESC," else "")+"sort_key,title,media.id"
        while(found.size<100 && more) {
            val clause=if(match.isEmpty())"" else " WHERE media.rowid IN (SELECT docid FROM names WHERE names MATCH ?)"
            val args=(if(match.isEmpty())emptyList() else listOf(match))+listOf("100",cursorOffset.toString())
            var rows=0
            db.query("SELECT uri,title,root,modified FROM media JOIN media_order ON media.id=media_order.id$clause ORDER BY $order LIMIT ? OFFSET ?",args.toTypedArray()).use {cursor->
                while(cursor.moveToNext()) {
                    rows++;cursorOffset++
                    if(cursor.getString(2) in allowed && !hidden(cursor.getString(0))) {
                        val media=QueueMedia(cursor.getString(0),cursor.getString(1));found+=media;dates[media.key]=cursor.getLong(3)
                    }
                    if(found.size==100)break
                }
            }
            // The visible page can fill partway through a batch after hidden rows were skipped.
            more=rows==100||found.size==100
        }
        return Result(found,if(more)cursorOffset else null,dates)
    }
    override fun close(){db.close()}
    companion object {
        internal val revision=java.util.concurrent.atomic.AtomicLong()
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
        fun terms(query:String)=MediaSearch.fts(query)
        private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    }
}
