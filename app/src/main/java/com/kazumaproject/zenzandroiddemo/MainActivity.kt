package com.kazumaproject.zenzandroiddemo

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var inputEditText: EditText
    private lateinit var outputTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var convertButton: Button

    @Volatile
    private var modelLoaded: Boolean = false

    private val modelAssetFileName = "ggml-model-Q5_K_M.gguf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // システムバーの余白調整（テンプレートそのまま）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // View の取得
        inputEditText = findViewById(R.id.inputEditText)
        outputTextView = findViewById(R.id.outputTextView)
        statusTextView = findViewById(R.id.statusTextView)
        convertButton = findViewById(R.id.convertButton)

        statusTextView.text = "モデル読み込み中…"
        convertButton.isEnabled = false

        // モデル読み込み（別スレッド）
        Thread {
            try {
                val modelFile = copyModelFromAssetsIfNeeded(modelAssetFileName)
                // JNI 経由で llama.cpp + zenz モデルを初期化
                LlamaBridge.initModel(modelFile.absolutePath)

                modelLoaded = true
                runOnUiThread {
                    statusTextView.text = "モデル読み込み完了 ✅"
                    convertButton.isEnabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    statusTextView.text = "モデル読み込み失敗 ❌"
                    Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }.start()

        // 変換ボタン押下
        convertButton.setOnClickListener {
            if (!modelLoaded) {
                Toast.makeText(this, "モデルを読み込み中です…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rawInput = inputEditText.text.toString().trim().toKatakana()
            if (rawInput.isEmpty()) {
                Toast.makeText(this, "カタカナまたはひらがなを入力してください", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            // v1 と同じ形: inputTag + input + outputTag
            // ※ toKatakana() 相当の処理を Kotlin 側でやりたいならここで変換してもOK
            val zenzPrompt = "\uEE00${rawInput}\uEE01"

            statusTextView.text = "変換中…"
            convertButton.isEnabled = false

            Thread {
                val result = try {
                    LlamaBridge.generate(zenzPrompt)
                } catch (e: Exception) {
                    e.printStackTrace()
                    "[error] ${e.message}"
                }

                runOnUiThread {
                    outputTextView.text = result
                    statusTextView.text = "完了"
                    convertButton.isEnabled = true
                }
            }.start()
        }
    }

    /**
     * assets/ にある GGUF モデルを /data/data/.../files/ にコピーして File を返す
     */
    private fun copyModelFromAssetsIfNeeded(fileName: String): File {
        val outFile = File(filesDir, fileName)
        if (outFile.exists()) {
            return outFile
        }

        try {
            assets.open(fileName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to copy model file from assets: $fileName", e)
        }

        return outFile
    }
}

fun String.toKatakana(): String {
    // ひらがな -> カタカナ簡易変換
    return map { c ->
        if (c in 'ぁ'..'ゖ') {
            (c.code + 0x60).toChar()
        } else c
    }.joinToString("")
}
