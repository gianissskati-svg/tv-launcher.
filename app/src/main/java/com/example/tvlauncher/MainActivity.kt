package com.example.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Scenic wallpaper drawn in code: sky gradient, sun glow, 3 mountain layers
class Scenery(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFF1B2A49.toInt(), 0xFF6B6FA8.toInt(), 0xFFF2A97B.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.72f, h * 0.55f, h * 0.35f, 0x66FFE2B8, 0x00FFE2B8, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val layers = listOf(
            Triple(0.62f, 0xFF4A4A7A.toInt(), 1.0f),
            Triple(0.72f, 0xFF35365F.toInt(), 1.7f),
            Triple(0.82f, 0xFF1F2140.toInt(), 2.6f)
        )
        for ((base, color, freq) in layers) {
            p.color
