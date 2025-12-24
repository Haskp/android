package com.example.myapplication
import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// кординаты и время
data class LocationRecord(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val time: String
)

class GisActivity : AppCompatActivity() {

    private lateinit var lockclient: FusedLocationProviderClient
    private lateinit var textView: TextView
    private lateinit var sendButton: Button
    private lateinit var refresh: Button
    private lateinit var startBackground: Button
    private lateinit var stopBackground: Button
    private lateinit var showRecords: Button
    private lateinit var sendAllSavedButton: Button
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentTime: String = ""

    private var backgroundJob: Job? = null
    private var isBackgroundRunning = false

    private val locationRecords = mutableListOf<LocationRecord>()

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val SERVER_IP = "192.168.0.44"
        private const val SERVER_PORT = 5000
        private const val SOCKET_TIMEOUT = 5000
        private const val BACKGROUND_INTERVAL = 5000L

        //  key save
        private const val PREFS_NAME = "location_prefs"
        private const val RECORDS_KEY = "saved_records"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gis)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        initViews()
        setupClick()
        LocPermission()

        loadRecordsFromPrefs()
    }

    private fun initViews() {
        textView = findViewById(R.id.gistxt)
        sendButton = findViewById(R.id.sendButton)
        refresh = findViewById(R.id.refreshButton)
        startBackground = findViewById(R.id.startBackground)
        stopBackground = findViewById(R.id.stopBackground)
        showRecords = findViewById(R.id.showRecords)
        sendAllSavedButton = findViewById(R.id.sendAllSavedButton)

        lockclient = LocationServices.getFusedLocationProviderClient(this)

        sendButton.isEnabled = false
        stopBackground.isEnabled = false
    }

    private fun setupClick() {
        refresh.setOnClickListener {
            getLoc()
        }

        sendButton.setOnClickListener {
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                val record = createLocationRecord()
                saveLocationRecord(record)
                sendServer(record)
            } else {
                showToast("Сначала получите координаты")
            }
        }

        startBackground.setOnClickListener {
            startBackgroundSending()
        }

        stopBackground.setOnClickListener {
            stopBackgroundSending()
        }

        showRecords.setOnClickListener {
            showAllRecords()
        }

        //Отправка всех сохраненных данных
        sendAllSavedButton.setOnClickListener {
            sendAllSavedDataToServer()
        }
    }

    // отправка всех сохраненных данных на сервер
    private fun sendAllSavedDataToServer() {
        if (locationRecords.isEmpty()) {
            showToast("Нет сохраненных данных для отправки")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    showToast("Начинаю отправку ${locationRecords.size} записей...")
                }

                var successCount = 0
                var errorCount = 0

                // Отправляем каждую запись отдельно
                locationRecords.forEachIndexed { index, record ->
                    try {
                        sendSingleRecordToServer(record)
                        successCount++

                        // Обновляем прогресс раз во сколько то записей
                        if (index % 5 == 0) {
                            withContext(Dispatchers.Main) {
                                textView.text = "Отправка: $index/${locationRecords.size}"
                            }
                        }

                        // зажержка отправки
                        delay(100)

                    } catch (e: Exception) {
                        errorCount++
                        println("Ошибка отправки записи $index: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = "Отправка завершена!\nУспешно: $successCount\nОшибок: $errorCount"
                    showToast(message)
                    upLocTxt()
                    println(message)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Ошибка массовой отправки: ${e.message}")
                }
            }
        }
    }

    //   отправка одной записи
    private suspend fun sendSingleRecordToServer(record: LocationRecord): Boolean {
        var socket: Socket? = null
        return try {
            val jsonData = recordToJson(record)
            println("Отправка сохраненной записи: $jsonData")

            socket = Socket()
            val socketAddress = InetSocketAddress(SERVER_IP, SERVER_PORT)
            socket.soTimeout = SOCKET_TIMEOUT
            socket.connect(socketAddress, SOCKET_TIMEOUT)

            val outputStream = socket.getOutputStream()
            val writer = OutputStreamWriter(outputStream, "UTF-8")
            writer.write(jsonData)
            writer.flush()

            val inputStream = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val response = reader.readLine()

            println("Ответ сервера для записи ${record.time}: $response")
            true

        } catch (e: Exception) {
            println("Ошибка отправки записи ${record.time}: ${e.message}")
            false
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    //   отправка всех данных одним пакетом
    private fun sendAllDataAsBatch() {
        if (locationRecords.isEmpty()) {
            showToast("Нет сохраненных данных для отправки")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            try {
                val jsonArray = JSONArray()
                locationRecords.forEach { record ->
                    jsonArray.put(JSONObject().apply {
                        put("latitude", record.latitude)
                        put("longitude", record.longitude)
                        put("time", record.time)
                        put("timestamp", record.timestamp)
                    })
                }

                val batchData = JSONObject().apply {
                    put("type", "batch")
                    put("records", jsonArray)
                    put("count", locationRecords.size)
                }.toString()

                println("Отправка batch данных: $batchData")

                socket = Socket()
                val socketAddress = InetSocketAddress(SERVER_IP, SERVER_PORT)
                socket.soTimeout = 10000 // Увеличиваем таймаут для batch отправки
                socket.connect(socketAddress, 10000)

                val outputStream = socket.getOutputStream()
                val writer = OutputStreamWriter(outputStream, "UTF-8")
                writer.write(batchData)
                writer.flush()

                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val response = reader.readLine()

                withContext(Dispatchers.Main) {
                    showToast("Все данные отправлены!\nОтвет: $response")
                    println("Ответ сервера на batch: $response")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Ошибка batch отправки: ${e.message}")
                    println("Ошибка batch отправки: ${e.message}")
                }
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startBackgroundSending() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("Сначала получите разрешение на локацию")
            return
        }

        isBackgroundRunning = true
        startBackground.isEnabled = false
        stopBackground.isEnabled = true

        backgroundJob = CoroutineScope(Dispatchers.IO).launch {
            while (isBackgroundRunning) {
                try {
                    getFreshLocation()

                    //запись data class
                    if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                        val record = createLocationRecord()
                        saveLocationRecord(record)
                        sendToServerInBackground(record)
                    }
                } catch (e: Exception) {
                    println("Ошибка в фоновой отправке: ${e.message}")
                }
                delay(BACKGROUND_INTERVAL)
            }
        }

        showToast("Фоновая запись запущена")
    }

    private fun stopBackgroundSending() {
        isBackgroundRunning = false
        backgroundJob?.cancel()
        startBackground.isEnabled = true
        stopBackground.isEnabled = false
        showToast("Фоновая запись остановлена")
    }

    private suspend fun getFreshLocation() {
        try {
            lockclient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(location.time))

                        runOnUiThread {
                            upLocTxt()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    println("Ошибка получения локации в фоне: ${e.message}")
                }

            delay(1000)

        } catch (e: Exception) {
            println("Ошибка получения локации: ${e.message}")
        }
    }

    private fun createLocationRecord(): LocationRecord {
        return LocationRecord(
            latitude = currentLatitude,
            longitude = currentLongitude,
            timestamp = System.currentTimeMillis(),
            time = currentTime.ifEmpty {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
        )
    }

    private fun saveLocationRecord(record: LocationRecord) {
        // Добавляем в список
        locationRecords.add(record)

        saveRecordsToPrefs()

        // логи
        println("=== СОХРАНЕНА ЗАПИСЬ ===")
        println("Время: ${record.time}")
        println("Широта: ${record.latitude}")
        println("Долгота: ${record.longitude}")
        println("========================")

        updateRecordsStats()
    }

    private fun saveRecordsToPrefs() {
        try {
            val jsonArray = JSONArray()

            locationRecords.forEach { record ->
                val jsonObject = JSONObject().apply {
                    put("latitude", record.latitude)
                    put("longitude", record.longitude)
                    put("timestamp", record.timestamp)
                    put("time", record.time)
                }
                jsonArray.put(jsonObject)
            }

            sharedPreferences.edit().putString(RECORDS_KEY, jsonArray.toString()).apply()
            println("Данные сохранены в SharedPreferences. Записей: ${locationRecords.size}")
        } catch (e: Exception) {
            println("Ошибка сохранения в SharedPreferences: ${e.message}")
        }
    }

    //  загрузка
    private fun loadRecordsFromPrefs() {
        try {
            val recordsJson = sharedPreferences.getString(RECORDS_KEY, null)

            if (!recordsJson.isNullOrEmpty()) {
                val jsonArray = JSONArray(recordsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val record = LocationRecord(
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        timestamp = obj.getLong("timestamp"),
                        time = obj.getString("time")
                    )
                    locationRecords.add(record)
                }
                println("Загружено записей из SharedPreferences: ${locationRecords.size}")
                updateRecordsStats() // Обновляем статистику после загрузки
            }
        } catch (e: Exception) {
            println("Ошибка загрузки данных: ${e.message}")
        }
    }

//clear
    private fun clearAllRecords() {
        locationRecords.clear()
        sharedPreferences.edit().remove(RECORDS_KEY).apply()
        updateRecordsStats()
        showToast("Все записи очищены")
        println("Все записи очищены")
    }

    private fun updateRecordsStats() {
        runOnUiThread {
            val statsText = "Записей: ${locationRecords.size}"
            println(statsText)
            upLocTxt()
        }
    }

    private fun LocPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            getLoc()
        }
    }

    private fun getLoc() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            textView.text = "Получение координат..."
            sendButton.isEnabled = false

            lockclient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(Date(location.time))

                        upLocTxt()
                        sendButton.isEnabled = true
                        showToast("Координаты получены успешно")
                    } else {
                        textView.text = "Местоположение недоступно\nПопробуйте обновить"
                        sendButton.isEnabled = false
                        showToast("Не удалось получить местоположение")
                    }
                }
                .addOnFailureListener { e ->
                    textView.text = "Ошибка получения местоположения: ${e.message}"
                    sendButton.isEnabled = false
                    showToast("Ошибка получения местоположения")
                }
        } else {
            showToast("Нет разрешения на доступ к местоположению")
        }
    }

    private fun upLocTxt() {
        val backgroundStatus = if (isBackgroundRunning) "Фоновая запись активна" else ""
        // ★★★ ИЗМЕНЕНО: Добавлено "(сохранено)" к количеству записей
        val recordsCount = "Записей: ${locationRecords.size} (сохранено)"
        val locationText = """
            Текущие координаты:
            Широта: $currentLatitude
            Долгота: $currentLongitude
            Время: $currentTime
            
            $recordsCount
            $backgroundStatus
        """.trimIndent()
        textView.text = locationText
    }

    private fun sendServer(record: LocationRecord) {
        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            try {
                val jsonData = recordToJson(record)
                println("Отправка данных: $jsonData")

                socket = Socket()
                val socketAddress = InetSocketAddress(SERVER_IP, SERVER_PORT)
                socket.soTimeout = SOCKET_TIMEOUT
                socket.connect(socketAddress, SOCKET_TIMEOUT)

                val outputStream = socket.getOutputStream()
                val writer = OutputStreamWriter(outputStream, "UTF-8")
                writer.write(jsonData)
                writer.flush()

                val inputStream = socket.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val response = reader.readLine()

                withContext(Dispatchers.Main) {
                    showToast("Данные отправлены!\nОтвет: $response")
                    println("Ответ сервера: $response")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Ошибка отправки: ${e.message}")
                    println("Ошибка отправки: ${e.message}")
                }
                e.printStackTrace()
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun sendToServerInBackground(record: LocationRecord) {
        var socket: Socket? = null
        try {
            val jsonData = recordToJson(record)
            println("Фоновая отправка: $jsonData")

            socket = Socket()
            val socketAddress = InetSocketAddress(SERVER_IP, SERVER_PORT)
            socket.soTimeout = SOCKET_TIMEOUT
            socket.connect(socketAddress, SOCKET_TIMEOUT)

            val outputStream = socket.getOutputStream()
            val writer = OutputStreamWriter(outputStream, "UTF-8")
            writer.write(jsonData)
            writer.flush()

            val inputStream = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val response = reader.readLine()

            println("Фоновый ответ сервера: $response")

        } catch (e: Exception) {
            println("Ошибка фоновой отправки: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun recordToJson(record: LocationRecord): String {
        return JSONObject().apply {
            put("latitude", record.latitude)
            put("longitude", record.longitude)
            put("time", record.time)
            put("timestamp", record.timestamp)
        }.toString()
    }

    // ★★★ ДОБАВЛЕНО ДЛЯ СОХРАНЕНИЯ: Кнопка очистки в диалоге
    private fun showAllRecords() {
        if (locationRecords.isEmpty()) {
            showToast("Нет записей")
            return
        }

        val recordsText = StringBuilder()
        recordsText.append("=== ВСЕ ЗАПИСИ (${locationRecords.size}) ===\n\n")

        locationRecords.takeLast(10).forEachIndexed { index, record ->
            val actualIndex = locationRecords.size - 10 + index + 1
            recordsText.append("Запись $actualIndex:\n")
            recordsText.append("  Время: ${record.time}\n")
            recordsText.append("  Координаты: ${record.latitude}, ${record.longitude}\n")
            recordsText.append("  ------------------\n")
        }

        if (locationRecords.size > 10) {
            recordsText.append("\n... и еще ${locationRecords.size - 10} записей")
        }

        // Создаем TextView для прокрутки
        val textView = TextView(this)
        textView.text = recordsText.toString()
        textView.setPadding(50, 20, 50, 20)
        textView.textSize = 14f

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("История записей")
            .setView(textView)
            .setPositiveButton("OK", null)
            // ★★★ ДОБАВЛЕНО ДЛЯ СОХРАНЕНИЯ: Кнопка очистки
            .setNeutralButton("Очистить все") { dialog, which ->
                clearAllRecords()
            }
            // ★★★ ДОБАВЛЕНО: Кнопка отправки всех данных
            .setNegativeButton("Отправить все на сервер") { dialog, which ->
                sendAllSavedDataToServer()
            }
            .show()
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@GisActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLoc()
                } else {
                    showToast("Разрешение на доступ к местоположению отклонено")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundSending()
    }
}