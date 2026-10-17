package com.novacamera.s24.core

/** Sinal de resultados da sessão, independente de ISO/EXIF e isolado de callbacks antigos.
 * Não mede quadros efetivamente apresentados na tela nem FPS do MP4.
 */
class CaptureProgress {
    data class Snapshot(val session:Long,val frames:Long)
    private var value=Snapshot(-1,0)
    @Synchronized fun begin(session:Long) { value=Snapshot(session,0) }
    @Synchronized fun frame(session:Long):Boolean {
        if(value.session!=session)return false
        value=Snapshot(session,value.frames+1)
        return true
    }
    @Synchronized fun snapshot()=value
    @Synchronized fun unchanged(sample:Snapshot)=value==sample

    companion object {
        fun canWatch(hasResults:Boolean,normalVideo:Boolean,hasRecorder:Boolean,exhausted:Boolean) =
            hasResults && (!normalVideo || (hasRecorder && !exhausted))
    }
}
