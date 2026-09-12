package com.caboperations.driver.data

import androidx.room.withTransaction
import com.caboperations.driver.location.LocationSnapshot
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class TripLocalRepository(private val context: android.content.Context) {
 private val db get()=CabDatabase.get(context)
 suspend fun startTrip(sessionId:String,driverId:String,vehicleId:String,startOdometer:Double,location:LocationSnapshot,pickup:String?=null,platformId:String?=null,notes:String?=null):String{
  require(startOdometer>=0){"Starting odometer cannot be negative"}; require(location.isUsable()){"GPS accuracy is insufficient or location is stale"}
  val id=UUID.randomUUID().toString(); val startedAt=Instant.ofEpochMilli(location.capturedAtEpochMs).toString()
  val payload=buildJsonObject{put("clientTransactionId",id);put("sessionId",sessionId);put("driverId",driverId);put("vehicleId",vehicleId);put("startedAt",startedAt);put("startOdometer",startOdometer);put("grossFare",0);put("additionalCharges",0);put("status","IN_PROGRESS");platformId?.let{put("platformId",it)};pickup?.let{put("pickup",it)};put("latitude",location.latitude);put("longitude",location.longitude);put("gpsAccuracyM",location.accuracyMeters.toDouble());put("gpsAt",startedAt);notes?.let{put("notes",it)}}.toString()
  db.withTransaction{OdometerGuard.requireAtLeast(db,sessionId,startOdometer,"Trip start odometer");require(db.localTripDao().active(sessionId)==null){"A trip is already in progress"};db.pendingTransactionDao().insert(PendingTransaction(id,"TRIP_START",payload,System.currentTimeMillis()));db.localTripDao().insert(LocalTrip(id,sessionId,startOdometer,null,0.0,"IN_PROGRESS",false))}
  return id
 }
 suspend fun endTrip(tripClientTransactionId:String,sessionId:String,driverId:String,vehicleId:String,endOdometer:Double,grossFare:Double,location:LocationSnapshot,paymentMethod:String?=null,additionalCharges:Double=0.0,status:String="COMPLETED",pickup:String?=null,dropoff:String?=null,platformId:String?=null,notes:String?=null):String{
  require(endOdometer>=0){"Ending odometer cannot be negative"};require(grossFare>=0){"Fare cannot be negative"};require(additionalCharges>=0){"Additional charges cannot be negative"};require(location.isUsable()){"GPS accuracy is insufficient or location is stale"}
  val active=db.localTripDao().active(sessionId)?:throw IllegalStateException("NO_ACTIVE_TRIP");require(active.clientTransactionId==tripClientTransactionId){"TRIP_MISMATCH"};OdometerGuard.requireAtLeast(db,sessionId,endOdometer,"Trip end odometer");require(endOdometer>=active.startOdometer){"Ending odometer cannot be less than trip start odometer"}
  val id=UUID.randomUUID().toString();val endedAt=Instant.ofEpochMilli(location.capturedAtEpochMs).toString();val payload=buildJsonObject{put("clientTransactionId",id);put("tripClientTransactionId",tripClientTransactionId);put("sessionId",sessionId);put("driverId",driverId);put("vehicleId",vehicleId);put("endedAt",endedAt);put("endOdometer",endOdometer);put("grossFare",grossFare);paymentMethod?.let{put("paymentMethod",it)};put("additionalCharges",additionalCharges);put("status",status);platformId?.let{put("platformId",it)};pickup?.let{put("pickup",it)};dropoff?.let{put("dropoff",it)};put("latitude",location.latitude);put("longitude",location.longitude);put("gpsAccuracyM",location.accuracyMeters.toDouble());put("gpsAt",endedAt);notes?.let{put("notes",it)}}.toString()
  db.withTransaction{db.pendingTransactionDao().insert(PendingTransaction(id,"TRIP_END",payload,System.currentTimeMillis()));db.localTripDao().finish(tripClientTransactionId,endOdometer,grossFare,status)}
  return id
 }
 suspend fun queueTrip(sessionId:String,driverId:String,vehicleId:String,startOdometer:Double,endOdometer:Double?,grossFare:Double,status:String,platformId:String?=null,pickup:String?=null,dropoff:String?=null,paymentMethod:String?=null,additionalCharges:Double=0.0,startedAt:String=Instant.now().toString(),endedAt:String?=null,notes:String?=null,location:LocationSnapshot):String{
  require(startOdometer>=0){"Start odometer cannot be negative"};require(endOdometer==null||endOdometer>=startOdometer){"End odometer cannot be less than start odometer"};require(grossFare>=0){"Gross fare cannot be negative"};require(additionalCharges>=0){"Additional charges cannot be negative"};require(location.isUsable()){"GPS accuracy is insufficient or location is stale"};val id=UUID.randomUUID().toString();val payload=buildJsonObject{put("clientTransactionId",id);put("sessionId",sessionId);put("driverId",driverId);put("vehicleId",vehicleId);platformId?.let{put("platformId",it)};put("startedAt",startedAt);endedAt?.let{put("endedAt",it)};pickup?.let{put("pickup",it)};dropoff?.let{put("dropoff",it)};put("startOdometer",startOdometer);endOdometer?.let{put("endOdometer",it)};put("grossFare",grossFare);paymentMethod?.let{put("paymentMethod",it)};put("additionalCharges",additionalCharges);put("status",status);put("latitude",location.latitude);put("longitude",location.longitude);put("gpsAccuracyM",location.accuracyMeters.toDouble());put("gpsAt",Instant.ofEpochMilli(location.capturedAtEpochMs).toString());notes?.let{put("notes",it)}}.toString();db.withTransaction{OdometerGuard.requireAtLeast(db,sessionId,startOdometer,"Trip start odometer");endOdometer?.let{OdometerGuard.requireAtLeast(db,sessionId,it,"Trip end odometer")};db.pendingTransactionDao().insert(PendingTransaction(id,"TRIP",payload,System.currentTimeMillis()));db.localTripDao().insert(LocalTrip(id,sessionId,startOdometer,endOdometer,grossFare,status,false))};return id
 }
}
