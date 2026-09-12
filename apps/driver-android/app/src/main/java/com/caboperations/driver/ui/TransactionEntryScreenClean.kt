package com.caboperations.driver.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.data.CabDatabase
import com.caboperations.driver.data.ExpenseLocalRepository
import com.caboperations.driver.data.FuelLocalRepository
import com.caboperations.driver.data.LocalTrip
import com.caboperations.driver.data.SyncScheduler
import com.caboperations.driver.data.TripLocalRepository
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

private val cleanPayments = listOf("CASH", "UPI", "CARD", "BANK", "OTHER")

@Composable
fun TransactionEntryScreenClean(type:String,sessionId:String,driverId:String,vehicleId:String,onSaved:(String)->Unit,onCancel:()->Unit){
    val context=LocalContext.current; val db=remember{CabDatabase.get(context)}; val tripRepo=remember{TripLocalRepository(context)}; val scope=rememberCoroutineScope()
    var activeTrip by remember{mutableStateOf<LocalTrip?>(null)}; var startedAt by remember{mutableStateOf<String?>(null)}; var odo by remember{mutableStateOf("")}; var endOdo by remember{mutableStateOf("")}; var fare by remember{mutableStateOf("")}; var charges by remember{mutableStateOf("0")}; var payment by remember{mutableStateOf("UPI")}; var pickup by remember{mutableStateOf("")}; var drop by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var notes by remember{mutableStateOf("")}; var fuelType by remember{mutableStateOf("CNG")}; var qty by remember{mutableStateOf("")}; var rate by remember{mutableStateOf("")}; var unit by remember{mutableStateOf("KG")}; var busy by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}; var location by remember{mutableStateOf<LocationSnapshot?>(null)}; var menu by remember{mutableStateOf(false)}
    val isTrip=type=="TRIP"; val isFuel=type=="FUEL"; val q=qty.toDoubleOrNull(); val r=rate.toDoubleOrNull(); val total=if(q!=null&&r!=null&&q>0&&r>=0)q*r else null
    fun capture(){val p=FusedLocationProvider(context);if(!p.isLocationEnabled()){location=null;runCatching{context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))};return};p.currentLocation{l,_->location=l}}
    LaunchedEffect(Unit){capture();if(isTrip){activeTrip=withContext(Dispatchers.IO){db.localTripDao().active(sessionId)};startedAt=activeTrip?.let{withContext(Dispatchers.IO){db.pendingTransactionDao().find(it.clientTransactionId)}?.let{p->Regex("\\\"startedAt\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(p.payloadJson)?.groupValues?.get(1)}}}}
    LaunchedEffect(type){capture()}
    val canSave=location?.isUsable()==true&&!busy
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        CabHeader(when{isTrip&&activeTrip!=null->"End trip";isTrip->"Start trip";isFuel->"Record fuel";else->"Add expense"},when{isTrip->"Enter only the details needed for this trip.";isFuel->"Record the filling details.";else->"Record the expense details."},onBack=onCancel)
        Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            CabCard(color=CabPurpleSoft){Text("Saved automatically",color=CabPurple,fontWeight=FontWeight.Bold);Text("Your entry is saved on the phone first and sent to the server automatically.",color=CabGray)}
            CabCard{
                when{
                    isTrip&&activeTrip==null->{CabSectionLabel("START TRIP");OutlinedTextField(odo,{odo=it},label={Text("Starting odometer (km)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(pickup,{pickup=it},label={Text("Pickup (optional)")},modifier=Modifier.fillMaxWidth())}
                    isTrip->{Text("Trip started ${startedAt?.let{runCatching{Duration.between(Instant.parse(it),Instant.now()).toMinutes()}.getOrNull()}?:0} min ago",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Start odometer: ${activeTrip!!.startOdometer.toInt()} km",color=CabGray);OutlinedTextField(endOdo,{endOdo=it},label={Text("Ending odometer (km)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(fare,{fare=it},label={Text("Gross fare (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(charges,{charges=it},label={Text("Additional charges (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);Box{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text("Payment: $payment")};DropdownMenu(menu,{menu=false}){cleanPayments.forEach{p->DropdownMenuItem({Text(p)},{payment=p;menu=false})}}};OutlinedTextField(drop,{drop=it},label={Text("Drop (optional)")},modifier=Modifier.fillMaxWidth())}
                    isFuel->{CabSectionLabel("FUEL");OutlinedTextField(odo,{odo=it},label={Text("Odometer (km)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(fuelType,{fuelType=it},label={Text("Fuel type")},modifier=Modifier.fillMaxWidth(),singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(qty,{qty=it},label={Text("Quantity")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(unit,{unit=it},label={Text("Unit")},modifier=Modifier.weight(1f),singleLine=true)};OutlinedTextField(rate,{rate=it},label={Text("Rate (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);Text("Total: ${total?.let{"₹%.2f".format(it)}?:"—"}",fontWeight=FontWeight.Bold)}
                    else->{CabSectionLabel("EXPENSE");OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(odo,{odo=it},label={Text("Odometer (optional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                }
                if(!isFuel&& !isTrip){}
                OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},modifier=Modifier.fillMaxWidth(),minLines=2)
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            CabPrimaryButton(if(busy)"Saving…" else when{isTrip&&activeTrip!=null->"End Trip";isTrip->"Start Trip";else->"Save entry"},enabled=canSave,onClick={
                if(busy)return@CabPrimaryButton; val loc=location?:return@CabPrimaryButton; busy=true; error=""; scope.launch{try{val id=when{isTrip&&activeTrip==null->tripRepo.startTrip(sessionId,driverId,vehicleId,odo.toDoubleOrNull()?:error("Enter starting odometer"),loc,pickup.trim().ifBlank{null},null,notes.trim().ifBlank{null});isTrip->tripRepo.endTrip(activeTrip!!.clientTransactionId,sessionId,driverId,vehicleId,endOdo.toDoubleOrNull()?:error("Enter ending odometer"),fare.toDoubleOrNull()?:error("Enter fare"),loc,payment,charges.toDoubleOrNull()?:0.0,"COMPLETED",null,drop.trim().ifBlank{null},null,notes.trim().ifBlank{null});isFuel->FuelLocalRepository(context).queueFuel(sessionId,driverId,vehicleId,fuelType.trim(),odo.toDoubleOrNull()?:error("Enter odometer"),q?:error("Enter quantity"),unit.trim(),r?:error("Enter rate"),total?:error("Enter valid quantity and rate"),payment,notes=notes.trim().ifBlank{null},location=loc);else->ExpenseLocalRepository(context).queueExpense(sessionId,driverId,vehicleId,amount.toDoubleOrNull()?:error("Enter amount"),null,payment,odometer=odo.toDoubleOrNull(),notes=notes.trim().ifBlank{null},location=loc)};SyncScheduler.enqueue(context,BuildConfig.API_BASE_URL);onSaved(id)}catch(e:Exception){error=e.message?:"Unable to save this entry.";busy=false}}})
            if(isTrip&&activeTrip!=null)Text("You can go back and add fuel or another expense. This trip stays active.",color=CabGray,style=MaterialTheme.typography.bodySmall)
            CabSecondaryButton("Back to session",enabled=!busy,onClick=onCancel)
        }
    }
}
