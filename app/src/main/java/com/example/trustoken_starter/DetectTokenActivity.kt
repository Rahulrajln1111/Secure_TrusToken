package com.example.trustoken_starter

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.StandardCharsets

class DetectTokenActivity : AppCompatActivity() {
    private lateinit var tvTokenName: TextView
    private var fileDescriptor: Int = 0
    private lateinit var usbManager: UsbManager

    companion object {
        init {
            System.loadLibrary("native-lib")
        }

        private const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"
        var isTokenConnected = false

        fun hexStringToByteArray(s: String): ByteArray {
            return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        fun byteArrayToAsciiString(bytes: ByteArray?): String {
            return bytes?.toString(StandardCharsets.US_ASCII) ?: ""
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                val device: UsbDevice? = intent.extras?.getParcelable(UsbManager.EXTRA_DEVICE)
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    device?.let {
                        fileDescriptor = getFileDescriptor(usbManager, it)
                        if (libint(fileDescriptor) == 0) {
                            tvTokenName.text = "Trustoken"
                            isTokenConnected = true
                            Toast.makeText(this@DetectTokenActivity, "USB Permission Granted", Toast.LENGTH_SHORT).show()
                            proceedToLogin()
                        }
                    }
                } else {
                    Toast.makeText(this@DetectTokenActivity, "USB Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect_token)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        tvTokenName = findViewById(R.id.token_name)
        val detectTokenButton = findViewById<Button>(R.id.btnDetectToken)

        val filter = IntentFilter(ACTION_USB_PERMISSION)

        // ✅ Fix for Android 13+ dynamic receiver registration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        detectTokenButton.setOnClickListener {
            requestUsbPermission()
        }
    }

    private fun requestUsbPermission() {
        usbManager.deviceList.values.forEach { device ->
            if (isSmartCardReader(device)) {
                val flag = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flag)

                if (!usbManager.hasPermission(device)) {
                    usbManager.requestPermission(device, permissionIntent)
                } else {
                    // Already has permission, proceed
                    fileDescriptor = getFileDescriptor(usbManager, device)
                    if (libint(fileDescriptor) == 0) {
                        tvTokenName.text = "Trustoken"
                        isTokenConnected = true
                        proceedToLogin()
                    }
                }
            }
        }
    }

    private fun proceedToLogin() {
        Toast.makeText(this, "Token Detected, Redirecting...", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun isSmartCardReader(device: UsbDevice): Boolean {
        return if (device.vendorId == 10381 && device.productId == 64) {
            tvTokenName.text = "TrusToken Detected"
            true
        } else false
    }

    private fun getFileDescriptor(manager: UsbManager, device: UsbDevice): Int {
        return manager.openDevice(device)?.fileDescriptor ?: -1
    }

    external fun libint(int: Int): Int

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}
