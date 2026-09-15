package com.caboperations.driver.ui

import androidx.compose.foundation.background
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
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.data.CabDatabase
import com.caboperations.driver.data.ExpenseLocalRepository
import com.caboperations.driver.data.FuelLocalRepository
import com.caboperations.driver.data.LocalTrip
import com.caboperations.driver.data.SyncScheduler
import com.caboperations.driver.data.TripLocalRepository
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot
import com.caboperations.driver.network.PlatformOption
import com.caboperations.driver.network.PlatformRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant

private val cleanPayments = listOf("CASH", "UPI", "CARD", "BANK", "OTHER")

@Composable
fun TransactionEntryScreenClean(type:String,sessionId:String,driverId:String,vehicleId:String,onSaved:(String)->Unit,onCancel:()->Unit){
    val context=LocalContext.current; val db=remember{CabDatabase.get(context)}; val tripRepo=remember{TripLocalRepository(context)}; val scope=rememberCoroutineScope(); val auth=remember{AuthRepository(context)}
    var activeTrip by remember{mutableStateOf<LocalTrip?>(null)}; var startedAt by remember{mutableStateOf<String?>(null)}; var startPlatformId by remember{mutableStateOf<String?>(null)}; var odo by remember{mutableStateOf("")}; var endOdo by remember{mutableStateOf("")}; var fare by remember{mutableStateOf("")}; var toll by remember{mutableStateOf("")}; var parking by remember{mutableStateOf("")}; var payment by remember{mutableStateOf("UPI")}; var pickup by remember{mutableStateOf("")}; var drop by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var notes by remember{mutableStateOf("")}; var fuelType by remember{mutableStateOf("CNG")}; var qty by remember{mutableStateOf("")}; var rate by remember{mutableStateOf("")}; var unit by remember{mutableStateOf("KG")}; var busy by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}; var location by remember{mutableStateOf<LocationSnapshot?>(null)}; var menu by remember{mutableStateOf(false)}; var platformMenu by remember{mutableStateOf(false)}; var platforms by remember{mutableStateOf<List<PlatformOption>>(emptyList())}; var platformId by remember{mutableStateOf<String?>(null)}
    val isTrip=type=="TRIP"; val isFuel=type=="FUEL"; val q=qty.toDoubleOrNull(); val r=rate.toDoubleOrNull(); val total=if(q!=null&&r!=null&&q>0&&r>=0)q*r else null; val tollValue=toll.toDoubleOrNull()?:0.0; val parkingValue=parking.toDoubleOrNull()?:0.0; val totalCollected=(fare.toDoubleOrNull()?:0.0)+tollValue+parkingValue; val selectedPlatform=platforms.firstOrNull{it.id==(platformId ?: startPlatformId)}
    fun capture(){val p=FusedLocationProvider(context);if(!p.isLocationEnabled()){location=null;return};p.currentLocation{l,_->location=l}}
    LaunchedEffect(Unit){
        capture(); withContext(Dispatchers.IO){auth.session()?.let{s->platforms=PlatformRepository(BuildConfig.API_BASE_URL,s.accessToken).load().getOrDefault(emptyList())}}
        if(isTrip){activeTrip=withContext(Dispatchers.IO){db.localTripDao().active(sessionId)};activeTrip?.let{trip->withContext(Dispatchers.IO){db.pendingTransactionDao().find(trip.clientTransactionId)}?.let{p->runCatching{val o=Json.parseToJsonElement(p.payloadJson).jsonObject;startedAt=o["startedAt"]?.jsonPrimitive?.content;startPlatformId=o["platformId"]?.jsonPrimitive?.content}}}}
    }
    LaunchedEffect(type){capture()}
    val canSave=!busy && (!isTrip || activeTrip!=null || platformId!=null)
    Column(Modifier.fillMaxSize().background(CabPageBackground).verticalScroll(rememberScrollState()).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        CabHeader(when{isTrip&&activeTrip!=null->"End trip";isTrip->"Start trip";isFuel->"Record fuel";else->"Add expense"},when{isTrip->"Enter only the details needed for this trip.";isFuel->"Record the filling details.";else->"Record the expense details."},onBack=onCancel)
        Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            CabCard(color=CabPurpleSoft){Text("Saved automatically",color=CabPurple,fontWeight=FontWeight.Bold);Text("Your entry is saved on the phone first and sent to the server automatically.",color=CabGray)}
            CabCard{
                when{
                    isTrip&&activeTrip==null->{CabSectionLabel("START TRIP");Text("Platform",style=MaterialTheme.typography.labelLarge,color=CabGray);Box{OutlinedButton({platformMenu=true},Modifier.fillMaxWidth()){Text(selectedPlatform?.name ?: "Select platform")};DropdownMenu(platformMenu,{platformMenu=false}){platforms.forEach{p->DropdownMenuItem({Text(p.name)},{platformId=p.id;platformMenu=false})}}};OutlinedTextField(odo,{odo=it.filter{c->c.isDigit()||c=='.'}},label={Text("Starting odometer (km)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(pickup,{pickup=it},label={Text("Pickup (optional)")},modifier=Modifier.fillMaxWidth())}
                    isTrip->{Text("Trip started ${startedAt?.let{runCatching{Duration.between(Instant.parse(it),Instant.now()).toMinutes()}.getOrNull()}?:0} min ago",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("Platform: ${selectedPlatform?.name ?: "—"}",color=CabGray);Text("Start odometer: ${activeTrip!!.startOdometer.toInt()} km",color=CabGray);OutlinedTextField(endOdo,{endOdo=it.filter{c->c.isDigit()||c=='.'}},label={Text("Ending odometer (km)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(fare,{fare=it.filter{c->c.isDigit()||c=='.'}},label={Text("Gross fare (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);Text("Toll & parking (optional)",fontWeight=FontWeight.SemiBold,color=CabGray);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(toll,{toll=it.filter{c->c.isDigit()||c=='.'}},label={Text("Toll (₹)")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(parking,{parking=it.filter{c->c.isDigit()||c=='.'}},label={Text("Parking (₹)")},modifier=Modifier.weight(1f),singleLine=true)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(30,50,100).forEach{v->AssistChip(onClick={toll=((toll.toDoubleOrNull()?:0.0)+v).toString().removeSuffix(".0")},label={Text("+₹$v Toll")})}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(30,50,100).forEach{v->AssistChip(onClick={parking=((parking.toDoubleOrNull()?:0.0)+v).toString().removeSuffix(".0")},label={Text("+₹$v Parking")})}};Text("Total Collected: ₹%.2f".format(totalCollected),color=CabGreen,fontWeight=FontWeight.Bold);Box{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text("Payment: $payment")};DropdownMenu(menu,{menu=false}){cleanPayments.forEach{p->DropdownMenuItem({Text(p)},{payment=p;menu=false})}}};OutlinedTextField(drop,{drop=it},label={Text("Drop (optional)")},modifier=Modifier.fillMaxWidth())}
                    isFuel->{CabSectionLabel("FUEL");OutlinedTextField(odo,{odo=it},label={Text("Odometer (km)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(fuelType,{fuelType=it},label={Text("Fuel type")},modifier=Modifier.fillMaxWidth(),singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(qty,{qty=it},label={Text("Quantity")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(unit,{unit=it},label={Text("Unit")},modifier=Modifier.weight(1f),singleLine=true)};OutlinedTextField(rate,{rate=it},label={Text("Rate (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);Text("Total: ${total?.let{"₹%.2f".format(it)}?:"—"}",fontWeight=FontWeight.Bold)}
                    else->{CabSectionLabel("EXPENSE");OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(odo,{odo=it},label={Text("Odometer (optional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                }
                OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},modifier=Modifier.fillMaxWidth(),minLines=2)
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(isTrip&&activeTrip==null&&platforms.isEmpty())Text("Platforms are loading…",color=CabGray,style=MaterialTheme.typography.bodySmall)
            if(!isTrip)Text("Location is captured automatically when available.",color=CabGray,style=MaterialTheme.typography.bodySmall)
            CabPrimaryButton(if(busy)"Saving…" else when{isTrip&&activeTrip!=null->"End Trip";isTrip->"Start Trip";else->"Save entry"},enabled=canSave,onClick={
                if(busy)return@CabPrimaryButton; busy=true; error=""; scope.launch{try{val id=when{isTrip&&activeTrip==null->tripRepo.startTrip(sessionId,driverId,vehicleId,odo.toDoubleOrNull()?:error("Enter starting odometer"),location?.takeIf{it.isUsable()},pickup.trim().ifBlank{null},platformId,notes.trim().ifBlank{null});isTrip->{val t=tollValue;val p=parkingValue;tripRepo.endTrip(activeTrip!!.clientTransactionId,sessionId,driverId,vehicleId,endOdo.toDoubleOrNull()?:error("Enter ending odometer"),fare.toDoubleOrNull()?:error("Enter fare"),location?.takeIf{it.isUsable()},payment,t+p,t,p,"COMPLETED",null,drop.trim().ifBlank{null},startPlatformId,notes.trim().ifBlank{null})};isFuel->FuelLocalRepository(context).queueFuel(sessionId,driverId,vehicleId,fuelType.trim(),odo.toDoubleOrNull()?:error("Enter odometer"),q?:error("Enter quantity"),unit.trim(),r?:error("Enter rate"),total?:error("Enter valid quantity and rate"),payment,notes=notes.trim().ifBlank{null},location=location);else->ExpenseLocalRepository(context).queueExpense(sessionId,driverId,vehicleId,amount.toDoubleOrNull()?:error("Enter amount"),null,payment,odometer=odo.toDoubleOrNull(),notes=notes.trim().ifBlank{null},location=location)};SyncScheduler.enqueue(context,BuildConfig.API_BASE_URL);onSaved(id)}catch(e:Exception){error=e.message?:"Unable to save this entry.";busy=false}}})
            if(isTrip&&activeTrip!=null)Text("You can go back and add fuel or another expense. This trip stays active.",color=CabGray,style=MaterialTheme.typography.bodySmall)
            CabSecondaryButton("Back to session",enabled=!busy,onClick=onCancel)
        }
    }
}