package com.caboperations.driver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.network.ApiClient
import com.caboperations.driver.sync.SyncPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class SyncWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
 override suspend fun doWork():Result{val dao=CabDatabase.get(applicationContext).pendingTransactionDao();val pending=dao.pending().sortedWith(compareBy{priority(it.type)}).take(SyncPolicy.MAX_BATCH_SIZE);if(pending.isEmpty())return Result.success();val baseUrl=inputData.getString(KEY_BASE_URL)?:BuildConfig.API_BASE_URL;val auth=AuthRepository(applicationContext);val tokenResult=auth.refreshIfNeeded();if(tokenResult.isFailure)return Result.retry();var token=tokenResult.getOrNull()?.accessToken?:return Result.retry();var api=ApiClient(baseUrl,token);var retry=false
  for(item in pending){val json=runCatching{Json.parseToJsonElement(item.payloadJson).jsonObject}.getOrNull();if(json==null){dao.markFailed(item.clientTransactionId,"INVALID_LOCAL_PAYLOAD");continue};val driverId=json["driverId"]?.jsonPrimitive?.content;val vehicleId=json["vehicleId"]?.jsonPrimitive?.content;var authRetried=false
   fun path():String?=when(item.type){TYPE_SESSION_START->"/v1/sessions";TYPE_SESSION_CLOSE->json["sessionId"]?.jsonPrimitive?.content?.let{"/v1/sessions/$it/close"};TYPE_TRIP_START->"/v1/trips/start";TYPE_TRIP_END->"/v1/trips/end";TYPE_TRIP->"/v1/trips";TYPE_FUEL->"/v1/fuel";TYPE_EXPENSE->"/v1/expenses";else->null}
   fun body():String=item.payloadJson
   fun sessionIdFromObjectPath(objectPath:String):String?{val parts=objectPath.split('/');return if(parts.size>=3&&parts[0]=="sessions")parts[1]else null}
   fun attachOdometerFile():ApiClient.Result{val objectPath=json["objectPath"]?.jsonPrimitive?.content?:return ApiClient.Result(false,false,"INVALID_FILE_UPLOAD_PAYLOAD");val sessionId=json["sessionId"]?.jsonPrimitive?.content?:sessionIdFromObjectPath(objectPath)?:return ApiClient.Result(false,false,"SESSION_ID_MISSING");val fileId=json["fileId"]?.jsonPrimitive?.content?:return ApiClient.Result(false,false,"INVALID_FILE_UPLOAD_PAYLOAD");return api.post("/v1/sessions/$sessionId/odometer-file","{\"fileId\":\"$fileId\",\"objectPath\":${Json.encodeToString(objectPath)}}",driverId,vehicleId)}
   fun upload():ApiClient.Result?{val filePath=json["localFilePath"]?.jsonPrimitive?.content;val fileId=json["fileId"]?.jsonPrimitive?.content;val objectPath=json["objectPath"]?.jsonPrimitive?.content;val mime=json["mimeType"]?.jsonPrimitive?.content?:"image/jpeg";val captured=json["capturedAt"]?.jsonPrimitive?.content;if(filePath.isNullOrBlank()||fileId.isNullOrBlank()||objectPath.isNullOrBlank())return null;val file=File(filePath);if(!file.exists())return null;return api.uploadFile("/v1/files",fileId,objectPath,mime,file.readBytes(),captured)}
   val result=if(item.type==TYPE_FILE_UPLOAD){if(json["localFilePath"]?.jsonPrimitive?.content.isNullOrBlank()||json["fileId"]?.jsonPrimitive?.content.isNullOrBlank()||json["objectPath"]?.jsonPrimitive?.content.isNullOrBlank()){dao.markFailed(item.clientTransactionId,"INVALID_FILE_UPLOAD_PAYLOAD");continue};if(!File(json["localFilePath"]!!.jsonPrimitive.content).exists()){dao.markFailed(item.clientTransactionId,"LOCAL_FILE_MISSING");continue};val uploaded=upload()!!;if(uploaded.success)attachOdometerFile() else uploaded}else{val p=path();if(p==null){dao.markFailed(item.clientTransactionId,"UNSUPPORTED_TRANSACTION_TYPE");continue};api.post(p,body(),driverId,vehicleId)}
   if(result.success){dao.markSynced(item.clientTransactionId);continue};if(result.authExpired&&!authRetried){val refreshed=auth.refreshIfNeeded(force=true);val t=refreshed.getOrNull()?.accessToken;if(t!=null){token=t;api=ApiClient(baseUrl,token);authRetried=true;val p=path();val rr=if(item.type==TYPE_FILE_UPLOAD){val up=upload();if(up?.success==true)attachOdometerFile() else up}else p?.let{api.post(it,body(),driverId,vehicleId)};if(rr?.success==true){dao.markSynced(item.clientTransactionId);continue};if(rr?.retryable==true)retry=true else dao.markFailed(item.clientTransactionId,rr?.error?:"AUTH_REFRESH_FAILED")}else retry=true}else if(result.retryable)retry=true else dao.markFailed(item.clientTransactionId,result.error?:"SYNC_FAILED")
   if(!result.success&&item.type==TYPE_SESSION_START)break
  };return if(retry)Result.retry() else Result.success()}
 private fun priority(type:String)=when(type){TYPE_SESSION_START->0;TYPE_TRIP_START->10;TYPE_TRIP_END->11;TYPE_TRIP->12;TYPE_FUEL,TYPE_EXPENSE->12;TYPE_SESSION_CLOSE->20;TYPE_FILE_UPLOAD->30;else->40}
 companion object{const val KEY_BASE_URL="api_base_url";const val TYPE_FILE_UPLOAD="FILE_UPLOAD";const val TYPE_SESSION_START="SESSION_START";const val TYPE_SESSION_CLOSE="SESSION_CLOSE";const val TYPE_TRIP_START="TRIP_START";const val TYPE_TRIP_END="TRIP_END";const val TYPE_TRIP="TRIP";const val TYPE_FUEL="FUEL";const val TYPE_EXPENSE="EXPENSE"}
}
