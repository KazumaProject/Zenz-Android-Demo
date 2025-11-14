package com.kazumaproject.zenzandroiddemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kazumaproject.zenz.ZenzEngine

class GenerateWithContextFragment : Fragment() {

    private lateinit var prevContextEditText: EditText
    private lateinit var inputEditText: EditText
    private lateinit var convertButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var outputTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_generate_with_context, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prevContextEditText = view.findViewById(R.id.prevContextEditText)
        inputEditText = view.findViewById(R.id.inputEditText)
        convertButton = view.findViewById(R.id.convertButton)
        statusTextView = view.findViewById(R.id.statusTextView)
        outputTextView = view.findViewById(R.id.outputTextView)

        convertButton.setOnClickListener {
            val activity = activity as? MainActivity
            if (activity == null || !activity.modelLoaded) {
                Toast.makeText(requireContext(), "モデルを読み込み中です…", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            val leftContext = prevContextEditText.text.toString().trim()
            val rawInput = inputEditText.text.toString().trim()

            if (rawInput.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "かな・カタカナを入力してください",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val katakana = rawInput.toKatakana()

            statusTextView.text = "変換中…"
            convertButton.isEnabled = false

            Thread {
                val result = try {
                    ZenzEngine.generateWithContext(
                        leftContext = leftContext,
                        input = katakana
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    "[error] ${e.message}"
                }

                activity.runOnUiThread {
                    outputTextView.text = result
                    statusTextView.text = "完了"
                    convertButton.isEnabled = true
                }
            }.start()
        }
    }
}
