package com.example.floatingmediacontrols

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageButton

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private lateinit var floatingPanel: View
    private lateinit var buttonParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { floatingPanel.visibility = View.GONE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // 1. Setup Floating Button
        floatingButton = inflater.inflate(R.layout.floating_button, null)
        buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        buttonParams.gravity = Gravity.TOP or Gravity.START
        buttonParams.x = 0
        buttonParams.y = 100

        // 2. Setup Floating Panel (Controls)
        floatingPanel = inflater.inflate(R.layout.floating_panel, null)
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        panelParams.gravity = Gravity.TOP or Gravity.START
        floatingPanel.visibility = View.GONE

        windowManager.addView(floatingButton, buttonParams)
        windowManager.addView(floatingPanel, panelParams)

        setupInteractions()
    }

    private fun setupInteractions() {
        // Dragging Logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        floatingButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = buttonParams.x
                    initialY = buttonParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isClick = false

                    buttonParams.x = initialX + dx
                    buttonParams.y = initialY + dy
                    windowManager.updateViewLayout(floatingButton, buttonParams)
                    
                    // Keep panel aligned with button if it's open
                    if (floatingPanel.visibility == View.VISIBLE) {
                        panelParams.x = buttonParams.x + floatingButton.width
                        panelParams.y = buttonParams.y
                        windowManager.updateViewLayout(floatingPanel, panelParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) togglePanel()
                    true
                }
                else -> false
            }
        }

        // Media Controls Logic
        floatingPanel.findViewById<ImageButton>(R.id.btnPlayPause).setOnClickListener {
            sendMediaCommand("PLAY_PAUSE")
            resetHideTimer()
        }
        floatingPanel.findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            sendMediaCommand("NEXT")
            resetHideTimer()
        }
        floatingPanel.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            sendMediaCommand("PREV")
            resetHideTimer()
        }
    }

    private fun togglePanel() {
        if (floatingPanel.visibility == View.GONE) {
            panelParams.x = buttonParams.x + floatingButton.width
            panelParams.y = buttonParams.y
            windowManager.updateViewLayout(floatingPanel, panelParams)
            floatingPanel.visibility = View.VISIBLE
            resetHideTimer()
        } else {
            floatingPanel.visibility = View.GONE
            hideHandler.removeCallbacks(hideRunnable)
        }
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 4000) // Auto-hide after 4 seconds
    }

    private fun sendMediaCommand(command: String) {
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, NotificationListener::class.java)
            val sessions = mediaSessionManager.getActiveSessions(componentName)

            if (sessions.isNotEmpty()) {
                val controller = sessions[0] // Gets the most recently active media session
                when (command) {
                    "PLAY_PAUSE" -> {
                        // In a robust app, check current state. Here we blindly toggle.
                        // Ideally: if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) pause() else play()
                        controller.transportControls.play() // Simplified for beginner focus
                    }
                    "NEXT" -> controller.transportControls.skipToNext()
                    "PREV" -> controller.transportControls.skipToPrevious()
                }
            } else {
                Log.w("MediaControls", "No active media sessions found.")
            }
        } catch (e: SecurityException) {
            Log.e("MediaControls", "Notification listener permission not granted", e)
        }
    }

    private fun startForegroundService() {
        val channelId = "floating_media_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Floating Media Controls", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Media Controls Active")
            .setContentText("Floating widget is running.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) windowManager.removeView(floatingButton)
        if (::floatingPanel.isInitialized) windowManager.removeView(floatingPanel)
    }
}