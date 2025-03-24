package com.example.trustoken_starter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale


class TrusToken : AppCompatActivity() {

    private lateinit var btnDetectToken: Button
    private lateinit var btnLogin: Button
    private lateinit var btnSign: Button
    private lateinit var btnVerify: Button
    private lateinit var btnEncrypt: Button
    private lateinit var btnDecrypt: Button
    private lateinit var btnLogout: Button
    private lateinit var btnClear: Button
    private lateinit var btnShare:Button
    private lateinit var tvTokenName: TextView
    private lateinit var tvSignature: TextView
    private lateinit var tvEncryptedData: TextView
    private lateinit var btnSelectOriginalFile: Button
    private lateinit var btnSelectSignatureFile: Button
    private lateinit var btnVerifySignature: Button
    private lateinit var btnSignDoc:Button
    private lateinit var edtIpAddress: EditText
    private lateinit var edtPort: EditText
    private lateinit var edtMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var tvReceivedMessage: TextView
    private var originalFileUri: Uri? = null
    private var signatureFileUri: Uri? = null
    private var encryptedFileUri: Uri? = null // Store the selected file URI
    private lateinit var btnSelectEncryptedFile: Button // Button to select the encrypted file
    private lateinit var btnDecryptFile: Button // Button to decrypt the selected file
    private lateinit var tvDecryptedText: TextView // TextView to display decrypted text


    private lateinit var edtPin: EditText
    private lateinit var edtPlainText: EditText
    private lateinit var edtPlainText2: EditText

    private var fileDescriptor: Int = 0
    private var isTokenConnected = false
    private var tokenPin: String = ""
    private var plainText: String = ""

    companion object {
        private const val REQUEST_CODE_ORIGINAL_FILE = 1001
        private const val REQUEST_CODE_SIGNATURE_FILE = 1002
        private const val REQUEST_CODE_ENCRYPTED_FILE = 1003

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

    private fun isHexString(str: String): Boolean {
        return str.matches(Regex("[0-9A-Fa-f]+"))
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT > 9) {
            val policy = android.os.StrictMode.ThreadPolicy.Builder().permitAll().build()
            android.os.StrictMode.setThreadPolicy(policy)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trus_token)

        btnDetectToken = findViewById(R.id.detect_token)
        btnLogin = findViewById(R.id.login)
        btnSign = findViewById(R.id.sign)
        btnVerify = findViewById(R.id.verify)
        btnEncrypt = findViewById(R.id.encrypt)
        btnDecrypt = findViewById(R.id.decrypt)
        btnLogout = findViewById(R.id.logout)
        btnClear = findViewById(R.id.clear_token)

        tvTokenName = findViewById(R.id.token_name)
        tvSignature = findViewById(R.id.signature)
        tvEncryptedData = findViewById(R.id.cipher_text)

        edtPin = findViewById(R.id.token_pin)
        edtPlainText = findViewById(R.id.plain_text)
        edtPlainText2 = findViewById(R.id.plain_text2)
        btnShare = findViewById(R.id.share_sign)
        btnSelectOriginalFile = findViewById(R.id.btnSelectOriginalFile)
        btnSelectSignatureFile = findViewById(R.id.btnSelectSignatureFile)
        btnVerifySignature = findViewById(R.id.btnVerifySignature)
        btnVerifySignature = findViewById(R.id.btnVerifySignature)
        btnSignDoc = findViewById(R.id.Sign_doc)

        edtIpAddress = findViewById(R.id.edtIpAddress)
        edtPort = findViewById(R.id.edtPort)
        edtMessage = findViewById(R.id.edtMessage)
        btnSend = findViewById(R.id.btnSend)
        tvReceivedMessage = findViewById(R.id.tvReceivedMessage)
        btnSelectEncryptedFile = findViewById(R.id.btnSelectEncryptedFile)
        btnDecryptFile = findViewById(R.id.btnDecryptFile)
        tvDecryptedText = findViewById(R.id.tvDecryptedText)

        btnDetectToken.setOnClickListener {
            fileDescriptor = detectSmartCard()
            if (libint(fileDescriptor) == 0) {
                tvTokenName.text = "Trustoken"
                isTokenConnected = true
            }
            Toast.makeText(this, "File Descriptor: $fileDescriptor", Toast.LENGTH_SHORT).show()
        }


         fun saveToFile(baseName: String, data: String, extension: String): File? {
            return try {
                val fileName = "${baseName}_${System.currentTimeMillis()}.$extension"
                val file = File(getExternalFilesDir(null), fileName)
                file.writeText(data, Charsets.UTF_8)

                Toast.makeText(this, "✅ File saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                println("Saved File Path: ${file.absolutePath}") // Debugging
                file // Return the saved file
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "❌ Failed to save file", Toast.LENGTH_SHORT).show()
                null
            }
        }
        fun hexStringToString(hexString: String): String {
            val output = StringBuilder()
            for (i in 0 until hexString.length step 2) {
                val hexChar = hexString.substring(i, i + 2)
                val charValue = hexChar.toInt(16) // Convert hex to integer
                output.append(charValue.toChar()) // Convert integer to character
            }
            return output.toString()
        }

        fun shareFile(fileName: String) {
            val file = File(filesDir, fileName) // Get the saved file
            if (!file.exists()) {
                Toast.makeText(this, "File does not exist", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = FileProvider.getUriForFile(this, "com.example.trustoken_starter.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"  // Set MIME type (change if needed)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Allow other apps to read
            }

            startActivity(Intent.createChooser(intent, "Share file via"))
        }
        val pickOriginalFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                originalFileUri = uri
                Toast.makeText(this, "✅ Original File Selected", Toast.LENGTH_SHORT).show()
                println("Original File URI: $originalFileUri") // Debugging
            } else {
                Toast.makeText(this, "⚠️ No file selected!", Toast.LENGTH_SHORT).show()
            }
        }

        val pickSignatureFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                signatureFileUri = uri
                Toast.makeText(this, "✅ Signature File Selected", Toast.LENGTH_SHORT).show()
                println("Signature File URI: $signatureFileUri") // Debugging
            } else {
                Toast.makeText(this, "⚠️ No file selected!", Toast.LENGTH_SHORT).show()
            }
        }

        fun uriToString(uri: Uri): String {
            return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        }
        fun uriToHexString(uri: Uri): String {
            return try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return ""
                inputStream.close()

                // Convert bytes to hex string in lowercase
                bytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.ROOT)
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
         fun shareFile(file: File) {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant access to file
            }
            startActivity(Intent.createChooser(intent, "Share Signature"))
        }
        fun readFileContent(uri: Uri): String {
            val inputStream: InputStream = contentResolver.openInputStream(uri)!!
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?

            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }

            bufferedReader.close()
            return stringBuilder.toString() // Returns the content as a string
        }

        var signatureFile: File? = null

        btnSignDoc.setOnClickListener {
            if (originalFileUri != null) {
                val fileHexString = uriToHexString(originalFileUri!!)
                if (fileHexString.isNotEmpty()) {
                    plainText = fileHexString.toString();
                    val signature = signData() // Call native function
                    signatureFile = saveToFile("signature", signature, "txt") // Store file reference
                    Toast.makeText(this, "✅ Signature Generated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠️ Could not read file content", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "⚠️ Select a file first!", Toast.LENGTH_SHORT).show()
            }
        }


        btnShare.setOnClickListener {
            if (signatureFile != null) {
                shareFile(signatureFile!!)
            } else {
                Toast.makeText(this, "⚠️ No signature file to share!", Toast.LENGTH_SHORT).show()
            }
        }
        btnSelectOriginalFile.setOnClickListener {
            pickOriginalFile.launch(arrayOf("*/*"))
        }

        btnSelectSignatureFile.setOnClickListener {
            pickSignatureFile.launch(arrayOf("*/*"))
        }


        // Verify signature
        btnVerifySignature.setOnClickListener {
            if (originalFileUri != null && signatureFileUri != null) {

                val result = verify(uriToString(signatureFileUri!!), uriToHexString(originalFileUri!!))

                Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Select both files first!", Toast.LENGTH_SHORT).show()
            }
        }
        btnSelectEncryptedFile.setOnClickListener {
            pickOriginalFile.launch(arrayOf("*/*")) // Call the file picker method
        }
        btnDecryptFile.setOnClickListener {
            if (originalFileUri != null) {
                try {
                    val encryptedHex = readFileContent(originalFileUri!!) // Read the encrypted hex content from file
                    val decryptedVal = decrypt(encryptedHex) // Decrypt the hex


                    // Display decrypted text in TextView
                    tvDecryptedText.text = hexStringToString(hexStringToString(decryptedVal))
                    Toast.makeText(this, "✅ Decryption Successful!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "❌ Decryption Failed!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "⚠️ Select an encrypted file first!", Toast.LENGTH_SHORT).show()
            }
        }

        fun sendToServer(ip: String, port: Int, message: String) {
            try {
                Log.d("DEBUG", "Connecting to $ip:$port")
                val socket = Socket(ip, port)

                val output = PrintWriter(socket.getOutputStream(), true)
                output.println(message)

                Log.d("DEBUG", "Message sent: $message")

                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("DEBUG", "❌ Error: ${e.message}")
            }
        }
        fun stringToHexString(msg: String): String {
            val stringBuilder = StringBuilder()
            for (i in msg.indices) {
                stringBuilder.append(String.format("%02X", msg[i].code))
            }
            return stringBuilder.toString().lowercase(Locale.ROOT)  // Convert to lowercase
        }
        fun sendEncryptedMessage(ip: String, port: Int, message: String) {
            Thread {
                try {

                    plainText = stringToHexString(message)
                    val signature = signData() // Sign the message using the signData() function
                    Log.d("SIGNATURE", signature)
                    Log.d("PPPPLLLTXT:",plainText)
                    val encryptedMessage = encrypt()
                    Log.d("PPPLLL",encryptedMessage)
                    // Open a socket connection
                    val socket = Socket(ip, port)
                    val outputStream = socket.getOutputStream()

                    // Send encrypted data
                    val encData = "Data: $encryptedMessage"
                    outputStream.write(encData.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val signatureText = " Signature: $signature"
                    outputStream.write(signatureText.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    // Close socket
                    outputStream.close()
                    socket.close()

                    Log.d("RAZZ", "✅ Encrypted Message Sent: $encryptedMessage")

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("RAZZ", "❌ Error Sending Message: ${e.message}")
                }
            }.start()
        }
        fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }
        btnSend.setOnClickListener {
            val ip = edtIpAddress.text.toString()
            val port = edtPort.text.toString().toIntOrNull() ?: return@setOnClickListener
            val message = edtMessage.text.toString()

            if (ip.isNotEmpty() && message.isNotEmpty()) {
                sendEncryptedMessage(ip, port, message)
            } else {
                Toast.makeText(this, "Enter valid IP, port, and message", Toast.LENGTH_SHORT).show()
            }
        }


        btnLogin.setOnClickListener {
            if (isTokenConnected && edtPin.text.toString().isNotEmpty()) {
                tokenPin = edtPin.text.toString()
                println("Token Pin: $tokenPin")
                val res = login(tokenPin)
                println("Login Response: $res")
                Toast.makeText(this, res, Toast.LENGTH_LONG).show()
                sendToServer("10.50.45.179",5554,"login_successes");
            }
        }


        btnSign.setOnClickListener {
            if (isTokenConnected && edtPlainText.text.toString().isNotEmpty()) {
                plainText = edtPlainText.text.toString()
                tvSignature.text = signData()
                saveToFile("signature.txt", signData(),"txt")
                Toast.makeText(this, "Signature saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Fill all the required fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnVerify.setOnClickListener {
            if(tvSignature.text.toString().isNotEmpty() && isHexString(tvSignature.text.toString()))
                tvSignature.text = verify(tvSignature.text.toString(), edtPlainText.text.toString())
        }

        btnEncrypt.setOnClickListener {
            if (isTokenConnected && edtPlainText2.text.toString().isNotEmpty()) {
                plainText = edtPlainText2.text.toString()
                tvEncryptedData.text = encrypt()
            }
        }

        btnDecrypt.setOnClickListener {
            if (isTokenConnected && tvEncryptedData.text.toString().isNotEmpty() && isHexString(tvEncryptedData.text.toString()))
            tvEncryptedData.text = byteArrayToAsciiString(hexStringToByteArray(decrypt(tvEncryptedData.text.toString())))
        }

        btnLogout.setOnClickListener {
            val res = logout()
//            val msg = if (res) "Logout Successful" else "Logout Failed"
            Toast.makeText(this,res , Toast.LENGTH_LONG).show()
        }

        btnClear.setOnClickListener {
            edtPin.text.clear()
            edtPlainText.text.clear()
            edtPlainText2.text.clear()
            tvSignature.text = ""
            tvEncryptedData.text = ""
        }
    }

    private fun detectSmartCard(): Int {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager?
        usbManager?.deviceList?.values?.forEach { device ->
            if (isSmartCardReader(device)) {
                val flag = if (Build.VERSION.SDK_INT >= 33) PendingIntent.FLAG_IMMUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), flag)
                usbManager.requestPermission(device, permissionIntent)
                if (usbManager.hasPermission(device)) {
                    return getFileDescriptor(usbManager, device)
                }
            }
        }
        return -1
    }

    private fun isSmartCardReader(device: UsbDevice): Boolean {
        return if (device.vendorId == 10381 && device.productId == 64) {
            tvTokenName.text = "Trustoken"
            true
        } else false
    }

    private fun getFileDescriptor(manager: UsbManager, device: UsbDevice): Int {
        return manager.openDevice(device)?.fileDescriptor ?: -1
    }

//    fun getTokenPin(): String {
//        return token_pin
//    }

    fun getPlainText(): String {
        return plainText
    }

//    external fun loadLibrary(libPath: String): Boolean
//    external fun openSession(): Boolean
    external fun libint(int: Int): Int
    external fun login(tokenPin: String): String
    external fun signData(): String
    external fun verify(string: String, plainText: String): String
    external fun encrypt(): String
    external fun decrypt(string: String): String
    external fun logout(): String
}


