package com.example.trustoken_starter

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

class DeviceSecurityCheck(private val context: Context) {

    // 1. Check for root using common methods
    private fun isDeviceRooted(): Boolean {
        return isDeviceRootedBySuBinary() || isSystemWritable() || hasRootManagementApp()
    }

    // Check for su binary
    private fun isDeviceRootedBySuBinary(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/system/app/Superuser.apk")
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    // Check if system partition is writable
    private fun isSystemWritable(): Boolean {
        val file = File("/system")
        return file.canWrite()
    }

    // Check for dangerous root management apps
    private fun hasRootManagementApp(): Boolean {
        val dangerousApps = listOf("com.noshufou.android.su", "com.topjohnwu.magisk")
        val pm = context.packageManager
        for (app in dangerousApps) {
            try {
                pm.getPackageInfo(app, PackageManager.GET_ACTIVITIES)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // app not found
            }
        }
        return false
    }

    // 2. Check if the device is an emulator
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
                Build.MODEL.contains("sdk") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("goldfish")
    }

    // 3. Check for potentially malicious apps
    private fun hasMaliciousApps(): Boolean {
        val maliciousApps = listOf("com.noshufou.android.su", "com.topjohnwu.magisk")
        val pm = context.packageManager
        for (app in maliciousApps) {
            try {
                pm.getPackageInfo(app, PackageManager.GET_ACTIVITIES)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // app not found
            }
        }
        return false
    }

    // 4. Check for dangerous permissions (e.g., writing to system folders)
    private fun hasDangerousPermissions(): Boolean {
        val dangerousPermissions = listOf(
            android.Manifest.permission.WRITE_SECURE_SETTINGS,

            )
        val pm = context.packageManager
        for (permission in dangerousPermissions) {
            try {
                val result = pm.checkPermission(permission, context.packageName)
                if (result == PackageManager.PERMISSION_GRANTED) {
                    return true
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
        return false
    }



    // 6. Combine all checks into a single result
    fun isDeviceCompromised(): Boolean {
        Log.e("ROOTED", isDeviceRooted().toString())
        Log.e("isEmulator", isEmulator().toString())
        Log.e("HasMalacious", hasMaliciousApps().toString())
        Log.e("HasDangerousPerm",hasDangerousPermissions().toString())
        return isDeviceRooted() || isEmulator() || hasMaliciousApps() || hasDangerousPermissions() ;
    }
}