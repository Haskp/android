package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var resultTextView: TextView
    private var currentInput = ""
    private var lastOperation: String? = null
    private var previousInput = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.textView)


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


        val buttonAdd: Button = findViewById(R.id.button14) // "+"
        val buttonSubtract: Button = findViewById(R.id.button15) // "-"
        val buttonEquals: Button = findViewById(R.id.button13) // "="
        val buttonClear: Button = findViewById(R.id.button17) // "C"
        val buttonMultiply: Button = findViewById(R.id.button19) // "*"
        val buttonDivide: Button = findViewById(R.id.button18) // "/"


        button1.setOnClickListener { appendNumber("1") }
        button2.setOnClickListener { appendNumber("2") }
        button3.setOnClickListener { appendNumber("3") }
        button4.setOnClickListener { appendNumber("4") }
        button5.setOnClickListener { appendNumber("5") }
        button6.setOnClickListener { appendNumber("6") }
        button7.setOnClickListener { appendNumber("7") }
        button8.setOnClickListener { appendNumber("8") }
        button9.setOnClickListener { appendNumber("9") }
        button0.setOnClickListener { appendNumber("0") }

        //обработчики
        buttonAdd.setOnClickListener { setOperation("+") }
        buttonSubtract.setOnClickListener { setOperation("-") }
        buttonMultiply.setOnClickListener { setOperation("*") }
        buttonDivide.setOnClickListener { setOperation("/") }

        //   "="
        buttonEquals.setOnClickListener { calculateResult() }

        buttonClear.setOnClickListener { clearInput() }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // добавляем цифры к текущему вводу
    private fun appendNumber(number: String) {
        currentInput += number
        upDisplay()
    }

    // операци (плюс, минус, умножение, деление)
    private fun setOperation(operation: String) {
        if (currentInput.isNotEmpty()) {
            if (previousInput.isEmpty()) {
                previousInput = currentInput
            }
            currentInput = ""
            lastOperation = operation
            upDisplay()
        }
    }

    // отображение
    private fun upDisplay() {
        val displayText = if (lastOperation != null) {
            "$previousInput $lastOperation $currentInput"
        } else {
            currentInput
        }
        resultTextView.text = displayText
    }

    //  вычисления
    private fun calculateResult() {
        if (currentInput.isNotEmpty() && previousInput.isNotEmpty() && lastOperation != null) {
            val num1 = previousInput.toDouble()
            val num2 = currentInput.toDouble()

            val result = when (lastOperation) {
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


            resultTextView.text = if (result is String) result else result.toString()


            currentInput = ""
            previousInput = result.toString()
            lastOperation = null
        }
    }


    private fun clearInput() {
        currentInput = ""
        previousInput = ""
        lastOperation = null
        resultTextView.text = "0"
    }
}
