package com.example.myapplication

import com.example.myapplication.R
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.zeromq.ZMQ
import kotlin.concurrent.thread

class SocketActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_socket)

        val sendButton = findViewById<Button>(R.id.sendButton)
        val responseText = findViewById<TextView>(R.id.responseText)

        sendButton.setOnClickListener {
            thread {
                try {
                    val context = ZMQ.context(1)
                    val socket = context.socket(ZMQ.REQ)
                    socket.connect("tcp://10.160.124.201:5000")

                    val message = "Hello!"
                    socket.send(message.toByteArray(ZMQ.CHARSET), 0) 
                    val reply = socket.recv(0)

                    runOnUiThread {
                        responseText.text = "Ответ сервера: ${String(reply, ZMQ.CHARSET)}"
                    }

                    socket.close()
                    context.close()
                }
                catch (e: Exception) {
                    runOnUiThread {
                        responseText.text = "Ошибка: ${e.message}"
                    }
                }
            }
        }
    }
}