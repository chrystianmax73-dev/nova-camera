package com.novacamera.s24.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Prova da captura, independente do zoom solicitado e da lente observada na prévia. */
object CaptureOptics {
    data class Sensor(val timestampNs:Long,val logicalId:String,val physicalId:String?,val focalMm:Float?,
        val iso:Int?,val exposureNs:Long?,val af:Int?,val ae:Int?,val awb:Int?)
    data class Evidence(val requestedZoom:Float,val requestedRoute:String?,val jpegFocalMm:Float?,
        val jpegEquivalentMm:Float?,val sensor:Sensor?,val compatibleId:String?,
        val opticalZoom:Float?,val confirmed:Boolean,val conflict:Boolean) {
        val digitalRatio get()=opticalZoom?.takeIf{it>0f}?.let { max(1f,requestedZoom/it) }
        fun report():String = "Óptica da foto: alvo=${fmt(requestedZoom)}× · rota solicitada=$requestedRoute"+
            " · físico no resultado=${sensor?.physicalId ?: "não informado"}"+
            " · ID confirmado=$confirmed · JPEG focal=${jpegFocalMm ?: "ausente"}mm/${jpegEquivalentMm ?: "ausente"}mm eq."+
            " · ${if(conflict) "CONFLITO entre JPEG e resultado; óptica não confirmada" else if(opticalZoom!=null) "${if(confirmed) "óptica" else "focal compatível com óptica"} ${fmt(opticalZoom)}× (ID $compatibleId), razão alvo/óptica≈${fmt(digitalRatio!!)}×" else "óptica não identificada"}"+
            " · ISO=${sensor?.iso} · exposição=${sensor?.exposureNs}ns · AF/AE/AWB=${sensor?.af}/${sensor?.ae}/${sensor?.awb}"
        private fun fmt(v:Float)=String.format(Locale.US,"%.2f",v)
    }
    fun evaluate(requestedZoom:Float,requestedRoute:String?,jpegFocalMm:Float?,jpegEquivalentMm:Float?,
        sensor:Sensor?,lenses:List<LensOption>):Evidence {
        val jpeg=jpegFocalMm?.takeIf{it.isFinite()&&it>0f}
        val actual=sensor?.focalMm?.takeIf{it.isFinite()&&it>0f}
        fun agrees(a:Float,b:Float)=abs(a-b)<=max(.05f,b*.025f)
        val byId=sensor?.physicalId?.let { id->lenses.firstOrNull{it.physicalCameraId==id || (!it.logical&&it.cameraId==id)} }
        val conflict=jpeg!=null && ((actual!=null && !agrees(jpeg,actual)) || (byId?.focalMm?.let{!agrees(jpeg,it)}==true))
        val candidates=lenses.filter{!it.logical && it.focalMm!=null && (jpeg ?: actual)?.let{f->agrees(f,it.focalMm)}==true}
            .distinctBy{it.physicalCameraId ?: it.cameraId}
        val chosen=if(conflict)null else byId ?: candidates.singleOrNull()
        val confirmed=!conflict && byId!=null && (jpeg==null || byId.focalMm?.let{agrees(jpeg,it)}!=false)
        return Evidence(requestedZoom,requestedRoute,jpeg,jpegEquivalentMm,sensor,chosen?.physicalCameraId ?: chosen?.cameraId,
            chosen?.intrinsic,confirmed,conflict)
    }
}
