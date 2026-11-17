package app.ember.tv.player

import app.ember.tv.data.toHex

/** Transport addresses live only in memory. Saved positions use a one-way identity. */
internal data class QueueMedia(val uri:String,val title:String,val identity:String?=null,val network:NetworkQueueEntry?=null,val directory:String?=null) {
    val key:String get()=identity ?: "file:"+java.security.MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).toHex()
}
internal class MediaQueue(items:List<QueueMedia>,val resources:List<java.io.Closeable> = emptyList()) {
    val items=items.toMutableList()
    init {require(items.size<=MAX_ITEMS) {"A queue can hold up to $MAX_ITEMS items."}}
    var index=0;private set
    var repeat="off"
    var playlistId:String?=null
    var shuffle=false
        set(value) {if(field!=value){field=value;rebuildOrder()}}
    private var order:List<Int> = emptyList()
    private fun rebuildOrder(){order=if(shuffle&&items.isNotEmpty())listOf(index)+(items.indices.filter {it!=index}).shuffled() else items.indices.toList()}
    private fun traversal():List<Int> {if(order.size!=items.size)rebuildOrder();return order}
    fun hasNext(accept:(QueueMedia)->Boolean={true}):Boolean {val values=traversal();return (if(repeat=="all")values else values.drop(values.indexOf(index)+1)).any {accept(items[it])}}
    val current get()=items.getOrNull(index)
    fun select(at:Int):QueueMedia? {if(at !in items.indices)return null;index=at;return current}
    fun next(ended:Boolean=false,accept:(QueueMedia)->Boolean={true}):QueueMedia? {
        if(ended&&repeat=="one")current?.takeIf(accept)?.let {return it}
        val values=traversal();val at=values.indexOf(index)
        val candidates=values.drop(at+1)+(if(repeat=="all")values.take(at+1) else emptyList())
        return candidates.firstOrNull {accept(items[it])}?.let(::select)
    }
    fun previous(accept:(QueueMedia)->Boolean={true}):QueueMedia? {
        val values=traversal();val at=values.indexOf(index)
        val candidates=values.take(at.coerceAtLeast(0)).asReversed()+(if(repeat=="all")values.drop(at.coerceAtLeast(0)).asReversed() else emptyList())
        return candidates.firstOrNull {accept(items[it])}?.let(::select)
    }
    fun add(values:List<QueueMedia>){
        require(values.size<=MAX_ITEMS-items.size) {"The queue is full. Create a separate playlist."}
        val previous=traversal();val first=items.size
        items.addAll(values)
        order=if(shuffle)previous+(first until items.size).shuffled() else items.indices.toList()
    }
    companion object {const val MAX_ITEMS=10000}
    fun move(from:Int,to:Int) {
        if(from !in items.indices||to !in items.indices)return
        val previous=traversal()
        fun moved(at:Int)=when {at==from->to;from<at&&to>=at->at-1;from>at&&to<=at->at+1;else->at}
        items.add(to,items.removeAt(from));index=moved(index)
        order=if(shuffle)previous.map(::moved) else items.indices.toList()
    }
    fun remove(at:Int):Boolean {
        if(at !in items.indices||at==index)return false
        val previous=traversal()
        items.removeAt(at);if(at<index)index--
        order=if(shuffle)previous.filter {it!=at}.map {if(it>at)it-1 else it} else items.indices.toList()
        return true
    }
}
internal object MediaQueues {
    private val queues=java.util.concurrent.ConcurrentHashMap<String,MediaQueue>()
    private val references=mutableMapOf<String,Int>()
    @Synchronized fun put(items:List<QueueMedia>,resources:List<java.io.Closeable> = emptyList()):String=java.util.UUID.randomUUID().toString().also {queues[it]=MediaQueue(items,resources);references[it]=1}
    fun get(id:String?)=id?.let(queues::get)
    fun launch(items:List<QueueMedia>,resources:List<java.io.Closeable> = emptyList(),open:(String)->Unit) {
        val id=try {put(items,resources)}catch(e:Exception){resources.forEach {runCatching {it.close()}};throw e}
        try {open(id)} catch(e:Exception) {release(id);throw e}
    }
    @Synchronized fun retain(id:String?):java.io.Closeable? {
        if(id==null||queues[id]==null)return null
        references[id]=(references[id]?:1)+1
        val closed=java.util.concurrent.atomic.AtomicBoolean()
        return java.io.Closeable {if(closed.compareAndSet(false,true))release(id)}
    }
    fun release(id:String?) {
        val closed=synchronized(this) {
            if(id==null||id !in references)return
            val count=references.getValue(id)-1
            if(count>0){references[id]=count;return}
            references.remove(id);queues.remove(id)
        }
        closed?.resources?.forEach {runCatching {it.close()}}
    }
}

/** A repeat range belongs to one item, and never silently repeats a previous title. */
internal class RepeatRange {
    var start:Long?=null;private set
    var end:Long?=null;private set
    fun markStart(position:Long){start=position.coerceAtLeast(0);end=null}
    fun markEnd(position:Long):Boolean {val first=start?:return false;if(position-first<1000)return false;end=position;return true}
    fun destination(position:Long):Long?=start?.takeIf {end!=null && position>=end!!}
    fun clear(){start=null;end=null}
}
