package com.example.myapplication

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.*
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.zeromq.ZMQ

data class LocationRecord(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val time: String,
    val gsmInfo: GsmInfo? = null
)

data class GsmInfo(
    val mcc: Int,
    val mnc: Int,
    val lac: Int,
    val cid: Int,

    val signalStrength: Int
)

class GisActivity : AppCompatActivity() {

    private lateinit var locClient: FusedLocationProviderClient
    private lateinit var textView: TextView
    private lateinit var sendButton: Button
    private lateinit var refresh: Button
    private lateinit var startBac: Button
    private lateinit var stopBac: Button
    private lateinit var AllSave: Button

    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0
    private var currentTime: String = ""

    private var bacJob: Job? = null
    private var BacRun = false

    private val locRec = mutableListOf<LocationRecord>()

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var telephonyManager: TelephonyManager

    private var zmqContext: ZMQ.Context? = null
    private var zmqSocket: ZMQ.Socket? = null

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val SERVER_IP = "192.168.0.44"
        private const val SERVER_PORT = 5555
        private const val BACKGROUND_INTERVAL = 1000L

        private const val PREFS_NAME = "location_prefs"
        private const val RECORDS_KEY = "saved_records"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gis)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        initViews()
        ClickListeners()
        cheklocprim()

        loadRec()
        initZmq()
    }

    private fun initViews() {
        textView = findViewById(R.id.gistxt)
        sendButton = findViewById(R.id.sendButton)
        refresh = findViewById(R.id.refreshButton)
        startBac = findViewById(R.id.startBackground)
        stopBac = findViewById(R.id.stopBackground)
        AllSave = findViewById(R.id.AllSave)

        locClient = LocationServices.getFusedLocationProviderClient(this)

        sendButton.isEnabled = false
        stopBac.isEnabled = false
    }

    private fun ClickListeners() {
        refresh.setOnClickListener {
            getlocation()
        }

        sendButton.setOnClickListener {
            if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                val record = LocRec()
                saveLocRec(record)
                sendServer(record)
            } else {
                showToast("Сначала получите координаты")
            }
        }

        startBac.setOnClickListener {
            BackSending()
        }
        stopBac.setOnClickListener {
            stopSending()
        }
        AllSave.setOnClickListener {
            sendallsavedata()
        }


    }

    private fun cheklocprim() {
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
            getlocation()
        }
    }

    private fun getlocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            sendButton.isEnabled = false

            locClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude
                        currentTime = time(location.time)

                        updateLocText()
                        sendButton.isEnabled = true
                        showToast("Координаты получены")
                    }
                }
        } else {
            showToast("Ошибка: нет разрешения на доступ к местоположению")
        }
    }

    private fun time(timeInMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeInMillis))
    }

    // ZMQ
    private fun initZmq() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                zmqContext = ZMQ.context(1)
                zmqSocket = zmqContext?.socket(ZMQ.REQ)
                zmqSocket?.connect("tcp://$SERVER_IP:$SERVER_PORT")
                zmqSocket?.setReceiveTimeOut(5000)
            } catch (e: Exception) {
                println("Ошибка инициализации ZMQ: ${e.message}")
            }
        }
    }

    // закрытие ZMQ
    private fun closeZmq() {
        try {
            zmqSocket?.close()
            zmqContext?.term()
        } catch (e: Exception) {
            println("Ошибка закрытия ZMQ: ${e.message}")
        }
    }

    private fun GsmInfo(): GsmInfo? {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }

            var gsmInfo: GsmInfo? = null

            val cellInfoList = telephonyManager.allCellInfo
            cellInfoList?.forEach { cellInfo ->
                when (cellInfo) {
                    is CellInfoGsm -> {
                        val cellIdentity = cellInfo.cellIdentity
                        val signalStrength = signallevel(cellInfo.cellSignalStrength)

                        gsmInfo = GsmInfo(
                            mcc = cellIdentity.mcc,
                            mnc = cellIdentity.mnc,
                            lac = cellIdentity.lac,
                            cid = cellIdentity.cid,

                            signalStrength = signalStrength
                        )
                        return@forEach
                    }
                }
            }

            gsmInfo
        } catch (e: Exception) {
            println("Ошибка получения GSM информации: ${e.message}")
            null
        }
    }

    private fun signallevel(cellSignalStrength: CellSignalStrength?): Int {
        return try {
            cellSignalStrength?.getDbm() ?: -1
        } catch (e: Exception) {
            -1
        }
    }
// запись место положения
    private fun LocRec(): LocationRecord {
        val gsmInfo = GsmInfo()
        return LocationRecord(
            latitude = currentLatitude,
            longitude = currentLongitude,
            timestamp = System.currentTimeMillis(),
            time = currentTime.ifEmpty {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            },
            gsmInfo = gsmInfo
        )
    }
// запись json
    private fun recordJson(record: LocationRecord): String {
        val jsonObject = JSONObject().apply {
            put("latitude", record.latitude)
            put("longitude", record.longitude)
            put("time", record.time)
            put("timestamp", record.timestamp)
        }

        record.gsmInfo?.let { gsm ->
            val gsmJson = JSONObject().apply {
                put("mcc", gsm.mcc)
                put("mnc", gsm.mnc)
                put("lac", gsm.lac)
                put("cid", gsm.cid)
                put("signal_strength", gsm.signalStrength)
            }
            jsonObject.put("gsm_info", gsmJson)
        }

        return jsonObject.toString()
    }

    private fun updateLocText() {
        val backgroundStatus = if (BacRun) "Фоновая запись активна" else ""
        val recordsCount = "Записей: ${locRec.size}"

        val locationText = """
            Текущие координаты:
            Время: $currentTime
            Широта: $currentLatitude
            Долгота: $currentLongitude
            
            $recordsCount
            $backgroundStatus
        """.trimIndent()

        textView.text = locationText
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getlocation()
            } else {
                Toast.makeText(
                    this,
                    "Разрешение на доступ к местоположению отклонено",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadRec() {
        try {
            val recordsJson = sharedPreferences.getString(RECORDS_KEY, null)

            if (!recordsJson.isNullOrEmpty()) {
                val jsonArray = JSONArray(recordsJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    val gsmInfo = if (obj.has("gsmInfo")) {
                        val gsmObj = obj.getJSONObject("gsmInfo")
                        GsmInfo(
                            mcc = gsmObj.optInt("mcc", -1),
                            mnc = gsmObj.optInt("mnc", -1),
                            lac = gsmObj.optInt("lac", -1),
                            cid = gsmObj.optInt("cid", -1),
                            signalStrength = gsmObj.optInt("signalStrength", -1)
                        )
                    } else {
                        null
                    }

                    val record = LocationRecord(
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        timestamp = obj.getLong("timestamp"),
                        time = obj.getString("time"),

                    )
                    locRec.add(record)
                }
                upRec()
            }
        } catch (e: Exception) {
            println("Ошибка загрузки данных: ${e.message}")
        }
    }

    private fun saveRec() {
        try {
            val jsonArray = JSONArray()

            locRec.forEach { record ->
                val jsonObject = JSONObject().apply {
                    put("latitude", record.latitude)
                    put("longitude", record.longitude)
                    put("timestamp", record.timestamp)
                    put("time", record.time)

                    record.gsmInfo?.let { gsm ->
                        val gsmJson = JSONObject().apply {
                            put("mcc", gsm.mcc)
                            put("mnc", gsm.mnc)
                            put("lac", gsm.lac)
                            put("cid", gsm.cid)
                            put("signalStrength", gsm.signalStrength)
                        }
                        put("gsmInfo", gsmJson)
                    }
                }
                jsonArray.put(jsonObject)
            }

            sharedPreferences.edit().putString(RECORDS_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
        }
    }

    private fun upRec() {
        runOnUiThread {
            updateLocText()
        }
    }

    private fun saveLocRec(record: LocationRecord) {
        locRec.add(record)
        saveRec()
        upRec()
    }

    private fun BackSending() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("Сначала получите разрешение на локацию")
            return
        }

        BacRun = true
        startBac.isEnabled = false
        stopBac.isEnabled = true

        bacJob = CoroutineScope(Dispatchers.IO).launch {
            while (BacRun) {
                try {
                    getlocation()

                    if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                        val record = LocRec()
                        saveLocRec(record)
                        sendServer(record)
                    }
                } catch (e: Exception) {
                    println("Ошибка в фоновой отправке: ${e.message}")
                }
                delay(BACKGROUND_INTERVAL)
            }
        }

        showToast("Фоновая запись запущена")
    }

    private fun stopSending() {
        BacRun = false
        bacJob?.cancel()
        startBac.isEnabled = true
        stopBac.isEnabled = false
        showToast("Фоновая запись остановлена")
    }

    // Отправка ZMQ
    private fun sendServer(record: LocationRecord) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (zmqSocket == null) {
                    initZmq()
                    delay(500)
                }

                if (zmqSocket == null) {
                    withContext(Dispatchers.Main) {
                        showToast("ZMQ не подключен")
                    }
                    return@launch
                }

                val jsonData = recordJson(record)

                val sent = zmqSocket?.send(jsonData)
                if (sent == true) {
                    val response = zmqSocket?.recvStr()

                    withContext(Dispatchers.Main) {
                        if (response != null) {
                            showToast("Данные отправлены!")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Ошибка отправки")
                }
            }
        }
    }

    private fun sendallsavedata() {
        if (locRec.isEmpty()) {
            showToast("Нет сохраненных данных для отправки")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                showToast(" Отправка ${locRec.size} записей...")
            }

            var successCount = 0

            locRec.forEachIndexed { index, record ->
                try {
                    if (RecServer(record)) {
                        successCount++
                    }

                    if (index % 5 == 0) {
                        withContext(Dispatchers.Main) {
                            textView.text = "Отправка: $index/${locRec.size}"
                        }
                    }

                    delay(100)

                } catch (e: Exception) {
                    println("Ошибка отправки записи $index: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                showToast("Отправка завершена! Успешно: ")
                updateLocText()
            }
        }
    }

    private  fun RecServer(record: LocationRecord): Boolean {
        return try {
            if (zmqSocket == null) {
                return false
            }

            val jsonData = recordJson(record)

            val sent = zmqSocket?.send(jsonData)
            if (sent == true) {
                zmqSocket?.recvStr()
                true
            } else {
                false
            }

        } catch (e: Exception) {
            false
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@GisActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSending()
        closeZmq()
    }
}