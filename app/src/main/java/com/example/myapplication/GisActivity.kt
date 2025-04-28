package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class GisActivity : AppCompatActivity() {

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройки osmdroid
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_gis)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)  // Это источник OpenStreetMap
        map.setMultiTouchControls(true)

        Log.d("GisActivity", "Checking permissions...")

        // Проверяем, есть ли разрешение на доступ к геоданным
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("GisActivity", "Permission not granted, requesting permission...")
            // Если нет, запрашиваем разрешение
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            Log.d("GisActivity", "Permission already granted")
            // Если разрешение уже есть, сразу запускаем настройку местоположения
            setupMyLocation()
        }
    }

    private fun setupMyLocation() {
        val locationOverlay = MyLocationNewOverlay(map)
        locationOverlay.enableMyLocation()
        map.overlays.add(locationOverlay)

        locationOverlay.runOnFirstFix {
            val myLocation: GeoPoint = locationOverlay.myLocation

            runOnUiThread {
                val controller: IMapController = map.controller
                controller.setZoom(15.0)  // Устанавливаем уровень масштабирования
                controller.setCenter(myLocation)

                // Обновляем TextView с координатами
                val coordsTextView = findViewById<TextView>(R.id.coordsTextView)
                coordsTextView.text = "Координаты: ${myLocation.latitude}, ${myLocation.longitude}"
            }
        }
    }

    // Обработка результата запроса на разрешение
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("GisActivity", "onRequestPermissionsResult called")

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("GisActivity", "Permission granted, setting up location")
                // Разрешение получено, запускаем настройку местоположения
                setupMyLocation()
            } else {
                Log.d("GisActivity", "Permission denied")
                // Разрешение не получено, сообщаем пользователю
                val coordsTextView = findViewById<TextView>(R.id.coordsTextView)
                coordsTextView.text = "Разрешение не предоставлено"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
