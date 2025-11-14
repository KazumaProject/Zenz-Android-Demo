package com.kazumaproject.zenzandroiddemo

// ひらがな -> カタカナ簡易変換
fun String.toKatakana(): String =
    map { c ->
        if (c in 'ぁ'..'ゖ') (c.code + 0x60).toChar() else c
    }.joinToString("")
