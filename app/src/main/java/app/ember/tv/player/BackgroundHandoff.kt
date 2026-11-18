package app.ember.tv.player

/** Staging a service never transfers ownership until the foreground engine has been released. */
internal class BackgroundHandoff<T>(initial:T) {
    enum class State { STAGED, COMMITTED, CLOSED }
    @Volatile var state=State.STAGED;private set
    @Volatile var retainedPreparation=false;private set
    @Synchronized fun retainPreparation():Boolean {
        if(state!=State.STAGED)return false
        retainedPreparation=true;return true
    }
    var value:T=initial;private set
    @Synchronized fun commit(updated:T):Boolean {
        if(state!=State.STAGED)return false
        value=updated;state=State.COMMITTED;return true
    }
    @Synchronized fun close():Boolean {
        if(state==State.CLOSED)return false
        val owned=state==State.COMMITTED;state=State.CLOSED;return owned
    }
}
