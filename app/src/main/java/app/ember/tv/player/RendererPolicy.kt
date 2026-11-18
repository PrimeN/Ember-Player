package app.ember.tv.player

import app.ember.tv.R

internal object RendererPolicy {
    val choices=listOf("auto" to R.string.ui_automatic,"gpu" to R.string.ui_stable_gpu,"gpu-next" to R.string.ui_advanced_gpu)
    fun normalized(value:String)=value.takeIf {v->choices.any {it.first==v}} ?: "auto"
    fun initial(value:String)=if(normalized(value)=="gpu")"gpu" else "gpu-next"
}

/** At most one automatic retry; only an explicit user selection resets recovery. */
internal class RendererSession(initial:String,private val videoEnabled:Boolean=true) {
    var active=initial;private set
    var recovered=false;private set
    fun select(value:String){active=value;recovered=false}
    fun fallback():Boolean {
        if(!videoEnabled||active!="gpu-next"||recovered)return false
        active="gpu";recovered=true;return true
    }
    fun initialize(open:(String)->Unit,recreate:()->Unit) {
        try {open(active)}catch(error:Throwable) {
            if(error !is Exception && error !is LinkageError || !fallback())throw error
            recreate();open(active)
        }
    }
}
