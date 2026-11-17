package com.cognify.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.cognify.R

class BackgroundAnimationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint()
    private var offset = 0f
    private var animator: ValueAnimator? = null
    
    // Theme aware colors
    private var color1: Int = 0
    private var color2: Int = 0
    private var color3: Int = 0

    init {
        paint.isAntiAlias = true
        color1 = androidx.core.content.ContextCompat.getColor(context, R.color.color_background)
        color2 = androidx.core.content.ContextCompat.getColor(context, R.color.color_background_secondary)
        color3 = androidx.core.content.ContextCompat.getColor(context, R.color.color_surface)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
        startAnimation()
    }

    private fun updateGradient() {
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Creating a dynamic sweeping/linear gradient
        paint.shader = LinearGradient(
            0f, -height + offset, width, height + offset,
            intArrayOf(color1, color2, color3, color2, color1),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.MIRROR
        )
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, height.toFloat() * 2).apply {
            duration = 15000 // Slow and subtle
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animation ->
                offset = animation.animatedValue as Float
                updateGradient()
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
