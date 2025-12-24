package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ProgramActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_program)

        val btnCalculator = findViewById<Button>(R.id.btnCalculator)
        btnCalculator.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        val btnMp3Player = findViewById<Button>(R.id.btnMp3Player)
        btnMp3Player.setOnClickListener {
            val intent = Intent(this, Mp3Activity::class.java)
            startActivity(intent)
        }

        val btnMap = findViewById<Button>(R.id.btnMap)
        btnMap.setOnClickListener {
            val intent = Intent(this, GisActivity::class.java)
            startActivity(intent)
        }

        // Новая кнопка для клиента
        val btnClient = findViewById<Button>(R.id.btnClient)
        btnClient.setOnClickListener {
            val intent = Intent(this, SocketActivity::class.java)
            startActivity(intent)
        }
    }
}