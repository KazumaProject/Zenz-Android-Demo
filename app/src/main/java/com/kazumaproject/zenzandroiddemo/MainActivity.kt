package com.kazumaproject.zenzandroiddemo

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kazumaproject.zenz.ZenzEngine
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    @Volatile
    var modelLoaded: Boolean = false   // ← Fragment から参照するので public に

    private val modelAssetFileName = "ggml-model-Q5_K_M.gguf"

    private lateinit var globalStatusTextView: TextView
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // システムバーの余白調整（テンプレート）
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        globalStatusTextView = findViewById(R.id.globalStatusTextView)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        globalStatusTextView.text = "モデル読み込み中…"

        // モデル読み込み（別スレッド）
        Thread {
            try {
                val modelFile = copyModelFromAssetsIfNeeded(modelAssetFileName)
                ZenzEngine.initModel(modelFile.absolutePath)

                modelLoaded = true
                runOnUiThread {
                    globalStatusTextView.text = "モデル読み込み完了 ✅"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    globalStatusTextView.text = "モデル読み込み失敗 ❌"
                    Toast.makeText(this, "Failed to load model: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }.start()

        // 最初の Fragment を表示
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GenerateFragment())
                .commit()
        }

        // BottomNavigation のリスナー
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_generate -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, GenerateFragment())
                        .commit()
                    true
                }

                R.id.nav_generate_with_context -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, GenerateWithContextFragment())
                        .commit()
                    true
                }

                R.id.nav_generate_with_conditions -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, GenerateWithContextAndConditionsFragment())
                        .commit()
                    true
                }

                else -> false
            }
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
