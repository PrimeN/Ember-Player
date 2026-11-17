package app.ember.tv.player

import android.content.*
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.ember.player.PrivateMedia
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.UsbFile
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only FAT32 access after an explicit Android USB grant. Each queue owns a lease. */
internal class DirectUsbSession private constructor(private val context:Context,val device:UsbMassStorageDevice):Closeable {
    private val lock=Any()
    private val references=java.util.concurrent.atomic.AtomicInteger(1)
    private val closed=AtomicBoolean()
    private val ownerReleased=AtomicBoolean()
    val connected=MutableStateFlow(true)
    val roots=device.partitions.map {it.fileSystem.rootDirectory}
    private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=UsbManager.ACTION_USB_DEVICE_DETACHED)return
        val removed=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE,android.hardware.usb.UsbDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        if(removed?.deviceId==device.usbDevice.deviceId)disconnect()
    }}
    init {ContextCompat.registerReceiver(context,receiver,IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),ContextCompat.RECEIVER_NOT_EXPORTED)}
    fun files(directory:UsbFile):List<UsbFile> = synchronized(lock) {ready();directory.listFiles().filter {it.isDirectory||DeviceFiles.media(it.name,"")}.sortedWith(compareByDescending<UsbFile>{it.isDirectory}.thenBy(NaturalOrder) {it.name})}
    fun lease():Closeable {
        while(true) {
            ready();val count=references.get();check(count>0)
            if(!references.compareAndSet(count,count+1))continue
            if(closed.get()){release();error("USB unavailable")}
            val released=AtomicBoolean();return Closeable {if(released.compareAndSet(false,true))release()}
        }
    }
    fun resource(file:UsbFile):RemoteMedia {
        val size=synchronized(lock){ready();file.length}
        return object:RemoteMedia {
            override val length=size
            override fun open(offset:Long):InputStream {
                require(offset in 0..size)
                return PositionedMediaInput(size,offset,{ready()}) {position,bytes,start,count->synchronized(lock) {
                    ready()
                    val buffer=ByteBuffer.wrap(bytes,start,count)
                    file.read(position,buffer)
                    buffer.position()-start
                }}
            }
        }
    }
    private fun ready(){check(!closed.get()&&connected.value&&!PrivateMedia.enabled) {"USB unavailable"}}
    private fun release(){if(references.decrementAndGet()<=0)disconnect()}
    private fun disconnect() {
        if(!closed.compareAndSet(false,true))return
        connected.value=false
        runCatching {context.unregisterReceiver(receiver)}
        Thread({synchronized(lock){runCatching {device.close()}}},"Ember USB close").apply {isDaemon=true;start()}
    }
    override fun close(){if(ownerReleased.compareAndSet(false,true))release()}
    companion object {
        fun open(context:Context,device:UsbMassStorageDevice):DirectUsbSession {
            check(!PrivateMedia.enabled)
            val manager=context.getSystemService(UsbManager::class.java)
            check(manager.hasPermission(device.usbDevice))
            try {device.init();check(device.partitions.isNotEmpty());return DirectUsbSession(context.applicationContext,device)}
            catch(e:Exception){runCatching {device.close()};throw e}
        }
    }
}
