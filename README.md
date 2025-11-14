# Zenz Android Demo (llama.cpp + ZenzEngine)

Android で **llama.cpp ベースの Zenz モデル**を使って  
かな漢字変換の実験ができるデモ & ライブラリです。

- ネイティブ部分は [llama.cpp](https://github.com/ggerganov/llama.cpp) ベース
- モデルは Zenz v3.1 xsmall GGUF ([Miwa-Keita/zenz-v3.1-xsmall-gguf](https://huggingface.co/Miwa-Keita/zenz-v3.1-xsmall-gguf)) を使用
- Kotlin からは `ZenzEngine` クラス 1 つで利用可能

> ⚠️ このリポジトリは **学習・検証用** です。本番アプリに組み込む場合は、モデルサイズ・メモリ使用量・ライセンスなどを必ず確認してください。

---

## Features / 特長

- ✅ Android から llama.cpp を呼び出す JNI ラッパー
- ✅ Zenz プロンプト形式（`\uEE00` / `\uEE01` / `\uEE02`）に対応
- ✅ 前コンテキスト（左文脈）付きのかな漢字変換をサポート
- ✅ Kotlin からは `ZenzEngine` 経由でシンプルに呼び出し
- ✅ サンプル Activity (`MainActivity`) 付き

---

## Project Structure / プロジェクト構成

```text
app/                    # サンプルアプリ (UI)
  src/main/java/...MainActivity.kt

zenz/                   # Android ライブラリモジュール
  src/main/cpp/
    CMakeLists.txt      # llama.cpp / ggml の .so をリンク
    zenz_bridge.cpp     # JNI ブリッジ (llama.cpp ラッパー)
  src/main/java/com/kazumaproject/zenz/
    ZenzEngine.kt       # Kotlin 側のラッパークラス
  src/main/jniLibs/     # libllama.so, libggml*.so (各 ABI)
```

---

## Requirements / 動作環境

- Android Studio (Giraffe 以降推奨)
- Android Gradle Plugin / Gradle はプロジェクト設定に準拠
- **minSdk 24+**
- **compileSdk 36**
- NDK がインストールされていること
- 端末に合わせた `libllama.so` / `libggml*.so` / GGUF モデル

---

## Setup / セットアップ手順

### 1. llama.cpp のビルドと .so 配置

1. PC (Linux / macOS / WSL など) で llama.cpp をビルドし、Android 用の  
   `libllama.so`, `libggml.so`, `libggml-base.so`, `libggml-cpu.so` を生成
2. 生成した `.so` を ABI ごとに以下に配置します：

```text
zenz/src/main/jniLibs/arm64-v8a/libllama.so
zenz/src/main/jniLibs/arm64-v8a/libggml.so
zenz/src/main/jniLibs/arm64-v8a/libggml-base.so
zenz/src/main/jniLibs/arm64-v8a/libggml-cpu.so
# 必要に応じて armeabi-v7a なども追加
```

`CMakeLists.txt` では、これらの `.so` を IMPORTED ライブラリとしてリンクしています。

```cmake
set(LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libggml.so)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libggml-base.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libggml-cpu.so)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
        ${LIB_DIR}/libllama.so)
```

---

### 2. GGUF モデルの配置

Zenz 用に学習された GGUF モデル
**`zenz-v3.1-xsmall-gguf` ([Miwa-Keita/zenz-v3.1-xsmall-gguf](https://huggingface.co/Miwa-Keita/zenz-v3.1-xsmall-gguf))**  
をダウンロードして、**サンプルアプリ側の `assets/`** に配置します。

```text
app/src/main/assets/ggml-model-Q5_K_M.gguf
```

アプリ起動時に、このファイルが内部ストレージにコピーされ、  
JNI 側 (`zenz_bridge.cpp`) からロードされます。

> ℹ️ **重要:**  
> モデル `zenz-v3.1-xsmall-gguf` は **CC BY-SA 4.0** ライセンスで提供されています。  
> 再配布やアプリへの同梱を行う場合は、著作権表記・クレジット・継承条件など、
> [Creative Commons Attribution-ShareAlike 4.0](https://creativecommons.org/licenses/by-sa/4.0/) の条件を必ず確認してください。

---

### 3. ライブラリモジュールの設定

`zenz/build.gradle` では、Android Library として設定しています（一例）：

```groovy
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace 'com.kazumaproject.zenz'
    compileSdk 36

    defaultConfig {
        minSdk 24
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = '11'
    }
}

dependencies {
    implementation libs.androidx.core.ktx
    implementation libs.androidx.appcompat
    implementation libs.material
    testImplementation libs.junit
    androidTestImplementation libs.androidx.junit
    androidTestImplementation libs.androidx.espresso.core
}
```

アプリ側の `settings.gradle` に `include(":zenz")` を追加して、  
`:app` モジュールから `implementation(project(":zenz"))` で参照します。

---

## Usage / 使い方

### 1. ZenzEngine の初期化

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var zenzEngine: ZenzEngine
    private var modelLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val modelFile = copyModelFromAssetsIfNeeded("ggml-model-Q5_K_M.gguf")

        zenzEngine = ZenzEngine(
            context = this,
            modelPath = modelFile.absolutePath
        )

        Thread {
            try {
                zenzEngine.initModel()
                runOnUiThread {
                    modelLoaded = true
                    // UI 更新など
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
```

### 2. かな漢字変換（オプションの左文脈付き）

```kotlin
val leftContext = prevContextEditText.text.toString().trim()
val reading = inputEditText.text.toString().trim().toKatakana()

if (reading.isNotEmpty() && modelLoaded) {
    statusTextView.text = "変換中…"
    convertButton.isEnabled = false

    Thread {
        val result = try {
            zenzEngine.convertWithContext(
                leftContext = leftContext,
                reading = reading,
                maxTokens = 32
            )
        } catch (e: Exception) {
            "[error] ${e.message}"
        }

        runOnUiThread {
            outputTextView.text = result
            statusTextView.text = "完了"
            convertButton.isEnabled = true
        }
    }.start()
}
```

`convertWithContext` は内部で JNI を呼び出し、以下のような Zenz 形式のプロンプトを組み立てます：

- 文脈あり： `\uEE02<leftContext>\uEE00<reading>\uEE01`
- 文脈なし： `\uEE00<reading>\uEE01`

---

## License / ライセンス

### ソースコード

このリポジトリ内の **ソースコード** は MIT License で提供されます。  
詳細は同梱の [`LICENSE`](./LICENSE) ファイルを参照してください。

### モデル (Zenz)

本プロジェクトで利用している Zenz モデル：

- 名前: **zenz-v3.1-xsmall-gguf**
- 配布元: [Miwa-Keita/zenz-v3.1-xsmall-gguf](https://huggingface.co/Miwa-Keita/zenz-v3.1-xsmall-gguf)
- ライセンス: **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**

アプリやプロダクトに組み込む場合は、**CC BY-SA 4.0 の条件に従う必要があります**。  
特に以下の点に注意してください：

- 著作者表示（Attribution）
- 同一ライセンスによる継承（ShareAlike）
- ライセンスの URL を明示すること

詳しくは公式のライセンス文書を参照してください：  
<https://creativecommons.org/licenses/by-sa/4.0/>

---

## Notes / 備考

- このプロジェクトは、**Android 上で Zenz + llama.cpp を試すためのデモ**です。
- 実際の日本語 IME などに統合する場合は、
  - 入力遅延
  - バッテリー消費
  - オフライン動作とプライバシー
  - エラー時のフォールバック
  などを考慮した設計・チューニングが必要です。

### llama.cpp フォークについて

本プロジェクトでは、標準の `ggerganov/llama.cpp` ではなく、**azooKey によるフォーク版**を使用しています。  
- リポジトリ: https://github.com/azooKey/llama.cpp.git  
- Zenz 向けの拡張や設定が含まれているため、このフォークを前提として JNI ブリッジ (`zenz_bridge.cpp`) を実装しています。


