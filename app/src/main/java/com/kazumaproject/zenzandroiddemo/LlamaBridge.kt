package com.kazumaproject.zenzandroiddemo


object LlamaBridge {
    init {
        System.loadLibrary("zenz_bridge")
    }

    external fun initModel(modelPath: String)
    external fun generate(prompt: String): String
    external fun generateWithContext(leftContext: String, input: String): String
}
