// JNI bridge to llama.cpp (AGENTS.md §9). Runs only in the ":llm" process.
// Privacy: nothing here logs a prompt, a question or generated text; llama.cpp logs are limited to
// warnings and errors (model loading problems).

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"

namespace {

constexpr const char * TAG = "citoyen-llm";

// Status codes returned to Kotlin (LlamaNative.kt).
constexpr jint OK = 0;
constexpr jint STOPPED = 1;
constexpr jint ERR_TOKENIZE = -1;
constexpr jint ERR_CONTEXT_FULL = -2;
constexpr jint ERR_GRAMMAR = -3;
constexpr jint ERR_DECODE = -4;

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    // Tokens currently in the KV cache of sequence 0: the common prefix with the next prompt (system
    // prompt and template header) is reused instead of being recomputed (AGENTS.md §8.6).
    std::vector<llama_token> cached;
    std::atomic<bool> stop{false};
};

void log_callback(ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_write(ANDROID_LOG_WARN, TAG, text);
    } else if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_write(ANDROID_LOG_ERROR, TAG, text);
    }
}

bool abort_requested(void * data) {
    return static_cast<Session *>(data)->stop.load();
}

std::string to_string(JNIEnv * env, jbyteArray bytes) {
    const jsize size = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(size), '\0');
    env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}

// The formatted prompt may already start with the BOS text written by the chat template: BOS is then
// not added a second time.
bool starts_with_bos(const Session & s, const std::string & text) {
    const llama_token bos = llama_vocab_bos(s.vocab);
    if (bos == LLAMA_TOKEN_NULL) return false;
    char buf[64];
    const int n = llama_token_to_piece(s.vocab, bos, buf, sizeof(buf), 0, true);
    return n > 0 && text.compare(0, static_cast<size_t>(n), buf, static_cast<size_t>(n)) == 0;
}

bool tokenize(const Session & s, const std::string & text, std::vector<llama_token> & out) {
    const bool add_special = !starts_with_bos(s, text);
    int n = -llama_tokenize(s.vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, add_special, true);
    if (n <= 0) return false;
    out.resize(static_cast<size_t>(n));
    n = llama_tokenize(s.vocab, text.data(), static_cast<int32_t>(text.size()), out.data(), n, add_special, true);
    return n >= 0;
}

// Decodes tokens[from..] after the reusable prefix, in batches of n_batch.
bool prefill(Session & s, std::vector<llama_token> & tokens) {
    size_t common = 0;
    while (common < s.cached.size() && common < tokens.size() && s.cached[common] == tokens[common]) common++;
    if (common == tokens.size()) common--;  // the last prompt token must be decoded to get its logits
    llama_memory_t mem = llama_get_memory(s.ctx);
    if (!llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(common), -1)) {
        llama_memory_clear(mem, true);
        common = 0;
    }
    s.cached.assign(tokens.begin(), tokens.begin() + static_cast<long>(common));
    const size_t n_batch = llama_n_batch(s.ctx);
    for (size_t i = common; i < tokens.size(); i += n_batch) {
        const size_t n = std::min(n_batch, tokens.size() - i);
        if (llama_decode(s.ctx, llama_batch_get_one(tokens.data() + i, static_cast<int32_t>(n))) != 0) {
            s.cached.clear();
            llama_memory_clear(mem, true);
            return false;
        }
        s.cached.insert(s.cached.end(), tokens.begin() + static_cast<long>(i), tokens.begin() + static_cast<long>(i + n));
    }
    return true;
}

// Greedy choice under the grammar. The best token is checked alone first; the grammar is applied to
// the whole vocabulary only when that token is not allowed (same idea as common/sampling.cpp).
llama_token sample(Session & s, llama_sampler * grammar, std::vector<llama_token_data> & all) {
    const float * logits = llama_get_logits_ith(s.ctx, -1);
    const int n_vocab = llama_vocab_n_tokens(s.vocab);
    llama_token best = 0;
    for (llama_token t = 1; t < n_vocab; t++) {
        if (logits[t] > logits[best]) best = t;
    }
    llama_token_data single{best, logits[best], 0.0f};
    llama_token_data_array one{&single, 1, -1, false};
    llama_sampler_apply(grammar, &one);
    if (std::isfinite(one.data[0].logit)) return best;

    all.resize(static_cast<size_t>(n_vocab));
    for (llama_token t = 0; t < n_vocab; t++) all[static_cast<size_t>(t)] = {t, logits[t], 0.0f};
    llama_token_data_array cur{all.data(), all.size(), -1, false};
    llama_sampler_apply(grammar, &cur);
    llama_token chosen = LLAMA_TOKEN_NULL;
    float top = -INFINITY;
    for (size_t i = 0; i < cur.size; i++) {
        if (cur.data[i].logit > top) {
            top = cur.data[i].logit;
            chosen = cur.data[i].id;
        }
    }
    return chosen;
}

Session * session(jlong handle) { return reinterpret_cast<Session *>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_init(JNIEnv * env, jobject, jstring nativeLibDir) {
    llama_log_set(log_callback, nullptr);
    const char * dir = env->GetStringUTFChars(nativeLibDir, nullptr);
    // Loads the CPU variant built for the best instruction set of this device (dotprod, i8mm, SVE…).
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(nativeLibDir, dir);
    llama_backend_init();

    std::string features;
    ggml_backend_reg_t cpu = ggml_backend_reg_by_name("CPU");
    if (cpu != nullptr) {
        auto get = reinterpret_cast<ggml_backend_get_features_t>(
            ggml_backend_reg_get_proc_address(cpu, "ggml_backend_get_features"));
        if (get != nullptr) {
            for (ggml_backend_feature * f = get(cpu); f->name != nullptr; f++) {
                if (!features.empty()) features += ' ';
                features += std::string(f->name) + "=" + f->value;
            }
        }
    }
    return env->NewStringUTF(features.c_str());
}

JNIEXPORT jlong JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_load(
    JNIEnv * env, jobject, jstring path, jint nCtx, jint nThreads, jboolean embeddings) {
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // CPU only; the file is memory-mapped by default
    const char * cpath = env->GetStringUTFChars(path, nullptr);
    llama_model * model = llama_model_load_from_file(cpath, mparams);
    env->ReleaseStringUTFChars(path, cpath);
    if (model == nullptr) return 0;

    auto * s = new Session();
    s->model = model;
    s->vocab = llama_model_get_vocab(model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx);
    cparams.n_batch = std::min<uint32_t>(512, static_cast<uint32_t>(nCtx));
    cparams.n_ubatch = cparams.n_batch;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    cparams.embeddings = embeddings == JNI_TRUE;
    cparams.no_perf = true;
    cparams.abort_callback = abort_requested;
    cparams.abort_callback_data = s;
    s->ctx = llama_init_from_model(model, cparams);
    if (s->ctx == nullptr) {
        llama_model_free(model);
        delete s;
        return 0;
    }
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    Session * s = session(handle);
    if (s == nullptr) return;
    if (s->ctx != nullptr) llama_free(s->ctx);
    if (s->model != nullptr) llama_model_free(s->model);
    delete s;
}

// Formats system + user with the chat template stored in the GGUF file (never re-coded here).
// Returns null when the file has no template or llama.cpp does not support it.
JNIEXPORT jbyteArray JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_formatChat(
    JNIEnv * env, jobject, jlong handle, jbyteArray system, jbyteArray user) {
    Session * s = session(handle);
    const char * tmpl = llama_model_chat_template(s->model, nullptr);
    if (tmpl == nullptr) return nullptr;
    const std::string sys = to_string(env, system);
    const std::string usr = to_string(env, user);
    const llama_chat_message messages[2] = {{"system", sys.c_str()}, {"user", usr.c_str()}};
    std::vector<char> buf(sys.size() + usr.size() + 1024);
    int n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n < 0) return nullptr;
    if (static_cast<size_t>(n) > buf.size()) {
        buf.resize(static_cast<size_t>(n));
        n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
        if (n < 0) return nullptr;
    }
    return to_bytes(env, std::string(buf.data(), static_cast<size_t>(n)));
}

JNIEXPORT jint JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_countTokens(JNIEnv * env, jobject, jlong handle, jbyteArray text) {
    std::vector<llama_token> tokens;
    return tokenize(*session(handle), to_string(env, text), tokens) ? static_cast<jint>(tokens.size()) : ERR_TOKENIZE;
}

JNIEXPORT void JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_requestStop(JNIEnv *, jobject, jlong handle) {
    session(handle)->stop.store(true);
}

// Greedy generation (temperature 0) under a GBNF grammar. The sink receives the prompt size, then
// each piece as UTF-8 bytes (a piece may end inside a multi-byte character; Kotlin decodes the
// stream); it returns false to stop.
JNIEXPORT jint JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_generate(
    JNIEnv * env, jobject, jlong handle, jbyteArray prompt, jbyteArray grammarText, jint maxTokens, jobject sink) {
    Session & s = *session(handle);
    s.stop.store(false);

    jclass sinkClass = env->GetObjectClass(sink);
    jmethodID onPrompt = env->GetMethodID(sinkClass, "onPrompt", "(I)V");
    jmethodID onPiece = env->GetMethodID(sinkClass, "onPiece", "([B)Z");

    std::vector<llama_token> tokens;
    if (!tokenize(s, to_string(env, prompt), tokens)) return ERR_TOKENIZE;
    if (tokens.size() + static_cast<size_t>(maxTokens) > llama_n_ctx(s.ctx)) return ERR_CONTEXT_FULL;
    env->CallVoidMethod(sink, onPrompt, static_cast<jint>(tokens.size()));

    const std::string gbnf = to_string(env, grammarText);
    llama_sampler * grammar = llama_sampler_init_grammar(s.vocab, gbnf.c_str(), "root");
    if (grammar == nullptr) return ERR_GRAMMAR;

    jint status = OK;
    if (!prefill(s, tokens)) {
        status = s.stop.load() ? STOPPED : ERR_DECODE;
    }
    std::vector<llama_token_data> all;
    char piece[256];
    for (int i = 0; status == OK && i < maxTokens; i++) {
        if (s.stop.load()) {
            status = STOPPED;
            break;
        }
        llama_token token = sample(s, grammar, all);
        if (token == LLAMA_TOKEN_NULL || llama_vocab_is_eog(s.vocab, token)) break;
        llama_sampler_accept(grammar, token);

        const int n = llama_token_to_piece(s.vocab, token, piece, sizeof(piece), 0, false);
        if (n > 0) {
            jbyteArray bytes = env->NewByteArray(n);
            env->SetByteArrayRegion(bytes, 0, n, reinterpret_cast<const jbyte *>(piece));
            const jboolean go_on = env->CallBooleanMethod(sink, onPiece, bytes);
            env->DeleteLocalRef(bytes);
            if (go_on == JNI_FALSE) {
                status = STOPPED;
                break;
            }
        }
        if (llama_decode(s.ctx, llama_batch_get_one(&token, 1)) != 0) {
            status = s.stop.load() ? STOPPED : ERR_DECODE;
            s.cached.clear();
            llama_memory_clear(llama_get_memory(s.ctx), true);
            break;
        }
        s.cached.push_back(token);
    }
    llama_sampler_free(grammar);
    return status;
}

// Pooled sentence embedding (embedding model loaded with embeddings = true); null on failure.
JNIEXPORT jfloatArray JNICALL
Java_fr_citoyende2027_llm_engine_LlamaNative_embed(JNIEnv * env, jobject, jlong handle, jbyteArray text) {
    Session & s = *session(handle);
    std::vector<llama_token> tokens;
    if (!tokenize(s, to_string(env, text), tokens)) return nullptr;
    if (tokens.size() > llama_n_batch(s.ctx)) tokens.resize(llama_n_batch(s.ctx));
    llama_memory_t mem = llama_get_memory(s.ctx);
    if (mem != nullptr) llama_memory_clear(mem, true);
    if (llama_decode(s.ctx, llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()))) != 0) {
        return nullptr;
    }
    const float * embedding = llama_get_embeddings_seq(s.ctx, 0);
    if (embedding == nullptr) return nullptr;
    const int n = llama_model_n_embd_out(s.model);
    jfloatArray out = env->NewFloatArray(n);
    env->SetFloatArrayRegion(out, 0, n, embedding);
    return out;
}

}  // extern "C"
