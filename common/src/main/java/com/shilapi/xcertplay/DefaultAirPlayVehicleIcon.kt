package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/** Built-in Ford-style vehicle tile used until the user chooses a custom CarPlay icon. */
internal object DefaultAirPlayVehicleIcon {
    fun png(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawOval(RectF(12f, 77f, 244f, 179f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 39, 78)
        })
        canvas.drawOval(RectF(16f, 81f, 240f, 175f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        })
        canvas.drawOval(RectF(20f, 85f, 236f, 171f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 39, 78)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        })
        val script = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 66f
            typeface = Typeface.create("cursive", Typeface.BOLD_ITALIC)
        }
        val baseline = 128f - (script.ascent() + script.descent()) / 2f
        canvas.drawText("Ford", 128f, baseline, script)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        output.toByteArray()
    }
}
