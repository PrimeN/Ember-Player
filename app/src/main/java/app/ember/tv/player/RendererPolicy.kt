package app.ember.tv.player

internal object RendererPolicy {
    val choices=listOf("auto" to "Automatic","gpu" to "Stable GPU","gpu-next" to "Advanced GPU")
    fun normalized(value:String)=value.takeIf {v->choices.any {it.first==v}} ?: "auto"
    fun initial(value:String,expand:Boolean,pqDisplay:Boolean)=when(normalized(value)) {
        "gpu-next"->"gpu-next";"gpu"->"gpu";else->if(expand&&pqDisplay)"gpu-next" else "gpu"
    }
}

/** At most one automatic retry; only an explicit user selection resets recovery. */
internal class RendererSession(initial:String) {
    var active=initial;private set
    var recovered=false;private set
    fun select(value:String){active=value;recovered=false}
    fun fallback():Boolean {
        if(active!="gpu-next"||recovered)return false
        active="gpu";recovered=true;return true
    }
    fun initialize(open:(String)->Unit,recreate:()->Unit) {
        try {open(active)}catch(error:Throwable) {
            if(error !is Exception && error !is LinkageError || !fallback())throw error
            recreate();open(active)
        }
    }
}
