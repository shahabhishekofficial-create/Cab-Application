package com.caboperations.driver.ui

/** Compatibility helper for legacy screen source; returns an app-local java.io.File. */
fun File(path: String): java.io.File = java.io.File(path)
