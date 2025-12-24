package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var resText: TextView
    private var tInput = ""
    private var lOp: String? = null
    private var pInput = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resText = findViewById(R.id.textView)


        val button1: Button = findViewById(R.id.button1)
        val button2: Button = findViewById(R.id.button2)
        val button3: Button = findViewById(R.id.button3)
        val button4: Button = findViewById(R.id.button4)
        val button5: Button = findViewById(R.id.button5)
        val button6: Button = findViewById(R.id.button6)
        val button7: Button = findViewById(R.id.button7)
        val button8: Button = findViewById(R.id.button8)
        val button9: Button = findViewById(R.id.button9)
        val button0: Button = findViewById(R.id.button0)


        val buttonAdd: Button = findViewById(R.id.button14)
        val buttonSubtract: Button = findViewById(R.id.button15)
        val buttonEquals: Button = findViewById(R.id.button13) //
        val buttonClear: Button = findViewById(R.id.button17)
        val buttonMultiply: Button = findViewById(R.id.button19)
        val buttonDivide: Button = findViewById(R.id.button18)

        button1.setOnClickListener { appdNumber("1") }
        button2.setOnClickListener { appdNumber("2") }
        button3.setOnClickListener { appdNumber("3") }
        button4.setOnClickListener { appdNumber("4") }
        button5.setOnClickListener { appdNumber("5") }
        button6.setOnClickListener { appdNumber("6") }
        button7.setOnClickListener { appdNumber("7") }
        button8.setOnClickListener { appdNumber("8") }
        button9.setOnClickListener { appdNumber("9") }
        button0.setOnClickListener { appdNumber("0") }


        buttonAdd.setOnClickListener { setOperation("+") }
        buttonSubtract.setOnClickListener { setOperation("-") }
        buttonMultiply.setOnClickListener { setOperation("*") }
        buttonDivide.setOnClickListener { setOperation("/") }


        buttonEquals.setOnClickListener { Result() }

        buttonClear.setOnClickListener { clear() } 


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun appdNumber(number: String) {
        tInput += number
        upDisplay()
    }

    private fun setOperation(operation: String) {
        if (tInput.isNotEmpty()) {
            if (pInput.isEmpty()) {
                pInput = tInput
            }
            tInput = ""
            lOp = operation
            upDisplay()
        }
    }

    private fun upDisplay() {
        val displayText = if (lOp != null) {
            "$pInput $lOp $tInput"
        } else {
            tInput
        }
        resText.text = displayText
    }

    private fun Result() {
        if (tInput.isNotEmpty() && pInput.isNotEmpty() && lOp != null) {
            val num1 = pInput.toDouble()
            val num2 = tInput.toDouble()

            val result = when (lOp) {
                "+" -> num1 + num2
                "-" -> num1 - num2
                "*" -> num1 * num2
                "/" -> {
                    if (num2 != 0.0) {
                        num1 / num2
                    } else {
                        "Error"
                    }
                }
                else -> 0.0
            }


            resText.text = if (result is String) result else result.toString()


            tInput = ""
            pInput = result.toString()
            lOp = null
        }
    }


    private fun clear() {
        tInput = ""
        pInput = ""
        lOp = null
        resText.text = "0"
    }
}
