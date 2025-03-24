package com.example.trustoken_starter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.StandardCharsets

class LoginActivity : AppCompatActivity() {
    private var tokenPin : String = "";
    private lateinit var btnLogin : Button ;
    private lateinit var edtPin : EditText ;
    private var isTokenConnected : Boolean = false ;

    companion object {
        init {
            System.loadLibrary("native-lib")
        }

        private const val ACTION_USB_PERMISSION = "com.example.USB_PERMISSION"

        fun hexStringToByteArray(s: String): ByteArray {
            return s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

        fun byteArrayToAsciiString(bytes: ByteArray?): String {
            return bytes?.toString(StandardCharsets.US_ASCII) ?: ""
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtPin = findViewById(R.id.etPin)
        btnLogin = findViewById(R.id.btnLogin)

        isTokenConnected = DetectTokenActivity.isTokenConnected

        btnLogin.setOnClickListener {
            if (isTokenConnected && edtPin.text.toString().isNotEmpty()) {
                tokenPin = edtPin.text.toString()
                println("Token Pin: $tokenPin")
                val res = login(tokenPin)
                println("Login Res: $res")
                Toast.makeText(this, res, Toast.LENGTH_LONG).show()

                // Check if login was successful
                if (res == "Login Success") {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish() // Close LoginActivity to prevent going back
                }
            } else {
                Toast.makeText(this, "Something Went Wrong", Toast.LENGTH_LONG).show()
            }
        }
    }

    external fun login(tokenPin: String): String
}
