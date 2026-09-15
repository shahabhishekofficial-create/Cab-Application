package com.caboperations.driver.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/** Compatibility helper for the hotfix screen. */
fun File(path: String): java.io.File = java.io.File(path)

fun Modifier.focusRequester(requester: FocusRequester): Modifier =
    androidx.compose.ui.focus.focusRequester(requester)
