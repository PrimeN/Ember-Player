package app.ember.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.ember.tv.player.*

@Composable
internal fun DeviceMediaRow(file:DeviceFile,modifier:Modifier=Modifier,selected:Boolean=false,click:()->Unit) {
    val context=LocalContext.current
    val preview by produceState(BrowserPreview(),file.uri,file.size,file.modified) {
        if(!PrivateMedia.hidden(file.uri.toString())||PrivateMedia.unlocked.value)value=DeviceThumbnails.preview(context,file)
    }
    PlayerFileRow(file.name,file.directory,modifier,selected,preview,DeviceThumbnails.audio(file.name),click)
}

@Composable
internal fun NetworkMediaRow(account:NetworkAccount,path:String,entry:NetworkEntry,selected:Boolean,click:()->Unit) {
    val preview by produceState(BrowserPreview(),account, path,entry) {
        if(!entry.directory&&entry.size>0)value=DeviceThumbnails.remote("${account.id}|${account.address}|${account.fingerprint}|$path|${entry.name}|${entry.size}",entry.name,
            NetworkFiles.resource(account,NetworkLocation.child(path,entry.name),entry.size))
    }
    PlayerFileRow(entry.name,entry.directory,selected=selected,preview=preview,audio=DeviceThumbnails.audio(entry.name),click=click)
}
