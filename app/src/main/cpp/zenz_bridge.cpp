// zenz_bridge.cpp

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "zenz-bridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "zenz-bridge", __VA_ARGS__)

// モデルと語彙のみグローバルで保持する。
// コンテキストは毎回生成・破棄する。
static llama_model *g_model = nullptr;
static const llama_vocab *g_vocab = nullptr;

// ------- 共通ヘルパー -------

// Swift の preprocessText とほぼ同じ:
// - 半角スペース -> 全角スペース (\u3000)
// - 改行は削除
static std::string preprocess_text(const std::string &text) {
    std::string out;
    out.reserve(text.size());

    for (unsigned char c: text) {
        if (c == ' ') {
            // UTF-8 の全角スペース U+3000
            out.append(u8"\u3000");
        } else if (c == '\n' || c == '\r') {
            // 捨てる
            continue;
        } else {
            out.push_back(static_cast<char>(c));
        }
    }
    return out;
}

// text を tokenize して llama_token の配列にする
static std::vector<llama_token> tokenize_text(const std::string &text, bool add_bos, bool add_eos) {
    std::vector<llama_token> tokens;

    if (!g_vocab) {
        return tokens;
    }

    const int32_t text_len = (int32_t) text.size();

    // 最初は適当に大きめ
    int32_t n_max = text_len + (add_bos ? 2 : 1);
    tokens.resize(n_max);

    int32_t n_tokens = llama_tokenize(
            g_vocab,
            text.c_str(),
            text_len,
            tokens.data(),
            n_max,
            add_bos,
            /*parse_special=*/false);

    if (n_tokens < 0) {
        // -n_tokens が必要な長さ
        n_max = -n_tokens;
        tokens.resize(n_max);
        n_tokens = llama_tokenize(
                g_vocab,
                text.c_str(),
                text_len,
                tokens.data(),
                n_max,
                add_bos,
                /*parse_special=*/false);
    }

    if (n_tokens <= 0) {
        tokens.clear();
        return tokens;
    }

    tokens.resize(n_tokens);

    if (add_eos) {
        tokens.push_back(llama_vocab_eos(g_vocab));
    }

    return tokens;
}

// 1トークン -> UTF-8 文字列
static std::string token_to_piece_str(llama_token token) {
    std::string out;
    if (!g_vocab) return out;

    // 最初は 8 バイト確保
    int32_t buf_size = 8;
    std::vector<char> buf(buf_size);

    int32_t n = llama_token_to_piece(
            g_vocab,
            token,
            buf.data(),
            buf_size,
            /*lstrip=*/0,
            /*special=*/false);

    if (n < 0) {
        buf_size = -n;
        buf.resize(buf_size);
        n = llama_token_to_piece(
                g_vocab,
                token,
                buf.data(),
                buf_size,
                0,
                false);
    }

    if (n > 0) {
        out.assign(buf.data(), buf.data() + n);
    }
    return out;
}

// llama_context を毎回生成するヘルパー
static llama_context *create_context() {
    if (!g_model) {
        return nullptr;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 512;  // ZenzContext と揃える
    cparams.n_threads = 4;    // 端末に合わせて調整してよい
    cparams.n_threads_batch = 4;
    cparams.n_batch = 512;

    llama_context *ctx = llama_init_from_model(g_model, cparams);
    if (!ctx) {
        LOGE("Failed to create llama_context");
    }
    return ctx;
}

// Swift の pure_greedy_decoding 相当
// ※ここで毎回コンテキストを生成・破棄する
static std::string pure_greedy_decoding(const std::string &leftSideContext, int maxCount) {
    if (!g_model || !g_vocab) {
        return "[error] model not initialized";
    }

    llama_context *ctx = create_context();
    if (!ctx) {
        return "[error] failed to create context";
    }

    // 1. 前処理 & トークナイズ (BOS なし, EOS なし)
    std::string pre = preprocess_text(leftSideContext);
    auto prompt_tokens = tokenize_text(pre, /*add_bos=*/false, /*add_eos=*/false);
    if (prompt_tokens.empty()) {
        llama_free(ctx);
        return "";
    }

    // 2. プロンプトを一度まとめて decode
    {
        llama_batch batch = llama_batch_get_one(
                prompt_tokens.data(),
                (int32_t) prompt_tokens.size()
        );
        int rc = llama_decode(ctx, batch);
        if (rc != 0) {
            LOGE("llama_decode(prompt) failed: %d", rc);
            llama_free(ctx);
            return "";
        }
    }

    // 3. greedy で maxCount トークンまで生成
    std::vector<llama_token> generated;
    generated.reserve(maxCount);

    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    for (int i = 0; i < maxCount; ++i) {
        // 直近トークンの logits
        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) {
            LOGE("logits is null");
            break;
        }

        // argmax を取る (softmax 不要：logits の大小だけ見れば OK)
        int best_id = 0;
        float best_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > best_logit) {
                best_logit = logits[tid];
                best_id = tid;
            }
        }

        llama_token next = (llama_token) best_id;
        if (next == eos) {
            break;
        }

        generated.push_back(next);

        // 次トークンを decode に食わせて、状態を進める
        llama_batch next_batch = llama_batch_get_one(&next, 1);
        int rc = llama_decode(ctx, next_batch);
        if (rc != 0) {
            LOGE("llama_decode(step) failed: %d", rc);
            break;
        }
    }

    // 4. 生成トークン列を detokenize
    std::string out;
    for (auto t: generated) {
        if (llama_vocab_is_control(g_vocab, t)) {
            // 制御トークンは表示しない
            continue;
        }
        out += token_to_piece_str(t);
    }

    // ★最後にコンテキストを破棄
    llama_free(ctx);

    return out;
}

// ------- JNI: モデル初期化 -------

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenzandroiddemo_LlamaBridge_initModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jModelPath
) {
    const char *c_model_path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("initModel: %s", c_model_path);

    // 再 init 時のクリーンアップ
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;   // Android は CPU 前提
    mparams.use_mmap = true;

    g_model = llama_model_load_from_file(c_model_path, mparams);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("Failed to get vocab");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return;
    }

    env->ReleaseStringUTFChars(jModelPath, c_model_path);
}

// ------- JNI: 「後半の変換結果」を返す -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenzandroiddemo_LlamaBridge_generate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jPrompt
) {
    if (!g_model || !g_vocab) {
        return env->NewStringUTF("Model not initialized");
    }

    const char *c_prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(c_prompt);
    env->ReleaseStringUTFChars(jPrompt, c_prompt);

    // prompt は Kotlin 側で \uEE00 + 入力 + \uEE01 の形で組み立てたものを想定
    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/32);

    return env->NewStringUTF(result.c_str());
}
