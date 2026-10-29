plugins {
    alias(libs.plugins.citoyen.android.library)
    alias(libs.plugins.citoyen.hilt)
}

// The model file is chosen by a human (AGENTS.md §9): Ministral-3-3B-Instruct-2512, Q4_K_M, from
// Mistral AI's GGUF repository; URL, SHA-256 and size (Git LFS pointer) given by the user on
// 2026-10-29. They can be overridden with the build properties below.
val modelUrl = providers.gradleProperty("citoyen.llm.url").orElse(
    "https://huggingface.co/mistralai/Ministral-3-3B-Instruct-2512-GGUF/resolve/main/" +
        "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
)
val modelSha256 = providers.gradleProperty("citoyen.llm.sha256")
    .orElse("9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8")
val modelSize = providers.gradleProperty("citoyen.llm.sizeBytes").orElse("2147023008")

android {
    namespace = "fr.citoyende2027.llm"
    // Versions used by the Android example of the pinned llama.cpp release (examples/llama.android).
    ndkVersion = "29.0.13113456"

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "MODEL_URL", "\"${modelUrl.get()}\"")
        buildConfigField("String", "MODEL_SHA256", "\"${modelSha256.get()}\"")
        buildConfigField("long", "MODEL_SIZE_BYTES", "${modelSize.get()}L")
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DBUILD_SHARED_LIBS=ON",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DGGML_NATIVE=OFF",
                    // One CPU library per instruction set (dotprod, i8mm, SVE…), the best one is loaded at
                    // run time (AGENTS.md §9).
                    "-DGGML_BACKEND_DL=ON",
                    "-DGGML_CPU_ALL_VARIANTS=ON",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DGGML_CPU_KLEIDIAI=OFF",
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

// The system prompt stays in llm/prompts/ (AGENTS.md §8, scanned by :tools:neutrality-check) and is
// packaged as an asset.
androidComponents {
    onVariants { variant -> variant.sources.assets?.addStaticSourceDirectory("prompts") }
}

tasks.withType<Test>().configureEach {
    // JVM test of the real bridge (CI job "Native bridge"): host build of src/main/cpp and test model.
    providers.gradleProperty("llm.hostLib").orNull?.let { systemProperty("llm.hostLib", it) }
    providers.gradleProperty("llm.testModel").orNull?.let { systemProperty("llm.testModel", it) }
}

dependencies {
    api(projects.rag)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.ktx)
    implementation(libs.hilt.ext.work)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.ext.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
}
