package com.dadsvictory.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * The two ways this app reaches outside itself.
 *
 * Both hand off to another app rather than doing anything themselves, which is why
 * neither needs a permission: no INTERNET for the browser, no CALL_PHONE for the
 * dialler. Dialling in particular only *opens* the dialler with the number filled
 * in — the app can never place a call on his behalf.
 */

fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to open that link.", Toast.LENGTH_SHORT).show()
    }
}

fun dialNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No dialler available. The number is $number.", Toast.LENGTH_LONG).show()
    }
}
