package app.ember.tv.player

import java.io.IOException
import java.io.InputStream

/** Independent cursor per read request; descriptor/device ownership stays with the source. */
internal class PositionedMediaInput(private val size:Long,start:Long,private val guard:()->Unit={},private val readAt:(Long,ByteArray,Int,Int)->Int):InputStream() {
    private var position=start
    @Volatile private var ended=false
    init {require(size>=0&&start in 0..size)}
    override fun read():Int {val value=ByteArray(1);return if(read(value,0,1)==1)value[0].toInt() and 255 else -1}
    @Synchronized override fun read(bytes:ByteArray,offset:Int,count:Int):Int {
        check(!ended);guard()
        require(offset>=0&&count>=0&&offset<=bytes.size-count)
        if(count==0)return 0
        if(position>=size)return -1
        val expected=minOf(count.toLong(),size-position,65536).toInt()
        val n=readAt(position,bytes,offset,expected)
        if(n !in 1..expected)throw IOException("Media read failed")
        position+=n;return n
    }
    override fun close(){ended=true}
}
