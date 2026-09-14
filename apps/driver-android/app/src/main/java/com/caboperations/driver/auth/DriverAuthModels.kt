package com.caboperations.driver.auth

import kotlinx.serialization.Serializable

@Serializable
data class TodayStats(
    val trips:Int = 0,
    val distanceKm:Double = 0.0,
    val earnings:Double = 0.0,
    val fuelSpend:Double = 0.0,
    val hoursActive:Double = 0.0
)

@Serializable
data class DriverContext(
    val userId:String,
    val driverId:String,
    val displayName:String,
    val vehicleId:String?=null,
    val registrationNumber:String?=null,
    val today:TodayStats=TodayStats()
)