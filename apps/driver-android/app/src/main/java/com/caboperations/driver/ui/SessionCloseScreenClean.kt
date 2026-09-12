package com.caboperations.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import com.caboperations.driver.data.SessionCloseLocalRepository
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot
import com.caboperations.driver.capture.CameraCapture
import com.caboperations.driver.capture.CameraPreviewController
import com.caboperations.driver.ocr.OdometerOcrEngine
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.coroutines.launch

@Composable
fun SessionCloseScreenClean(sessionId:String,driverId:String,vehicleId:String,startOdometer:Double,onClosed:()->Unit,onCancel:()->Unit){
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current; val scope=rememberCoroutineScope(); val repo=remember{SessionCloseLocalRepository(context)}
    var odo by remember{mutableStateOf("")}; var trips by remember{mutableStateOf("")}; var income by remember{mutableStateOf("")}; var notes by remember{mutableStateOf("")}; var capture by remember{mutableStateOf<ImageCapture?>(null)}; var photo by remember{mutableStateOf<String?>(null)}; var gps by remember{mutableStateOf<LocationSnapshot?>(null)}; var ocr by remember{mutableStateOf<OdometerOcrResult?>(null)}; var busy by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}
    fun captureGps(){val p=FusedLocationProvider(context);if(!p.isLocationEnabled()){gps=null;runCatching{context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))};return};p.currentLocation{l,_->gps=l}}
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){captureGps()}
    LaunchedEffect(Unit){val c=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;val l=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(!c||!l)permissions.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))else captureGps()}
    val close=odo.toDoubleOrNull(); val count=trips.toIntOrNull(); val total=income.toDoubleOrNull(); val ready=!busy&&close!=null&&close>=startOdometer&&count!=null&&count>=0&&total!=null&&total>=0&&photo!=null&&ocr!=null&&gps?.isUsable()==true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        CabHeader("Close session","Finish today’s work",onBack=onCancel)
        Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            CabCard(color=CabPurpleSoft){Text("Finish your day",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Take the final odometer photo and enter the day’s totals.",color=CabGray);Text("Started at ${startOdometer.toInt()} km",color=CabPurple,fontWeight=FontWeight.SemiBold)}
            CabCard{CabSectionLabel("FINAL ODOMETER PHOTO");if(photo==null){AndroidView(factory={PreviewView(it)},modifier=Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(16.dp)),update={v->CameraPreviewController(context).bind(owner,v){capture=it}});CabPrimaryButton(if(busy)"Reading…" else "Take photo & read",enabled=capture!=null&&!busy,onClick={val c=capture?:return@CabPrimaryButton;busy=true;CameraCapture(context).capture(c,"close_odo"){r->r.onSuccess{u->photo=u.toString();scope.launch{ocr=OdometerOcrEngine(context).recognize(u);busy=false;captureGps()}}.onFailure{error="Camera could not capture the photo.";busy=false}}})}else{Text("Photo captured ✓",color=CabGreen,fontWeight=FontWeight.Bold);CabSecondaryButton("Retake photo",enabled=!busy,onClick={photo=null;ocr=null})};ocr?.let{Text(if(it.reading!=null)"Camera read ${it.reading} km" else "Camera could not read the odometer. Enter it manually.",color=CabGray)}}
            CabCard{CabSectionLabel("TODAY’S TOTALS");OutlinedTextField(odo,{odo=it},label={Text("Closing odometer (km)")},supportingText={Text("Must be at least ${startOdometer.toInt()} km")},modifier=Modifier.fillMaxWidth(),singleLine=true);if(close!=null&&close>=startOdometer)Text("Distance today • ${"%.1f".format(close-startOdometer)} km",color=CabPurple,fontWeight=FontWeight.Bold);OutlinedTextField(trips,{trips=it.filter(Char::isDigit)},label={Text("Trips completed")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(income,{income=it},label={Text("Total income (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},modifier=Modifier.fillMaxWidth(),minLines=2)}
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            CabPrimaryButton(if(busy)"Saving…" else "Finish & Close Session",enabled=ready,onClick={val o=close?:return@CabPrimaryButton;val t=count?:return@CabPrimaryButton;val i=total?:return@CabPrimaryButton;val g=gps?:return@CabPrimaryButton;val p=photo?:return@CabPrimaryButton;val x=ocr?:return@CabPrimaryButton;busy=true;scope.launch{try{repo.queueCloseSession(sessionId,driverId,vehicleId,o,g.latitude,g.longitude,g.accuracyMeters,java.time.Instant.ofEpochMilli(g.capturedAtEpochMs).toString(),p,t,i,notes.trim().ifBlank{null},x,OdometerVerifier.compare(o,x.reading,x.confidence));onClosed()}catch(e:Exception){error=e.message?:"Unable to close session.";busy=false}}})
            CabSecondaryButton("Cancel",enabled=!busy,onClick=onCancel)
        }
    }
}
