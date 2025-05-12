package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class GisActivity : AppCompatActivity() {

    private lateinit var loc: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gis)

        loc = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            getloc()
        }
    }

    private fun getloc() {
        loc.lastLocation.addOnSuccessListener { location ->
            val gistxt = findViewById<TextView>(R.id.gistxt)

            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                gistxt.text = "Текущие координаты: $lat, $lon"
            } else {
                gistxt.text = "Не удалось получить координаты"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getloc()
        } else {
            Toast.makeText(this, "Требуется разрешение ", Toast.LENGTH_SHORT).show()
        }
    }
}