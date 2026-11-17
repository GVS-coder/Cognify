package com.cognify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.absoluteValue

object AvatarUtils {
    
    private val avatarColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", 
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", 
        "#009688", "#4CAF50", "#8BC34A", "#FF9800", 
        "#FF5722", "#795548", "#607D8B"
    )

    fun getAvatar(context: Context, name: String, sizeDp: Int = 100): Drawable {
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Determine initial and color
        val initial = if (name.isNotBlank()) name.first().uppercase() else "?"
        val colorHex = avatarColors[name.hashCode().absoluteValue % avatarColors.size]
        val bgColor = Color.parseColor(colorHex)

        // Draw circle
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        // Draw text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        
        // Calculate text vertical center
        val bounds = Rect()
        textPaint.getTextBounds(initial, 0, initial.length, bounds)
        val textHeight = bounds.height()
        val y = radius + (textHeight / 2f)

        canvas.drawText(initial, radius, y, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
