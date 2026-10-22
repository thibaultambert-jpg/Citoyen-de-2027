cmake_minimum_required(VERSION 3.22.1)

# JNI bridge to llama.cpp (AGENTS.md §9), built from source: llama.cpp is a git submodule pinned to
# release b11400 (commit 0bb496dbd3af0add77ff82c406a915b41e839d56, MIT).
project(citoyen_llm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(ANDROID)
    # Google Play: native libraries must support 16 KB memory pages.
    add_link_options("-Wl,-z,max-page-size=16384")
    # ggml writes its CPU variants (one per instruction set, chosen at run time) next to the runtime
    # output directory; keep them with the other libraries packaged in the APK.
    if(NOT CMAKE_RUNTIME_OUTPUT_DIRECTORY)
        set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})
    endif()
endif()

add_subdirectory(llama.cpp build-llama)

add_library(citoyen_llm SHARED citoyen_llm.cpp)
target_link_libraries(citoyen_llm PRIVATE llama ggml)

if(ANDROID)
    target_link_libraries(citoyen_llm PRIVATE log)
else()
    # Host build for the JVM test of the bridge (CI only): JNI headers from the JDK, log stub.
    find_package(JNI REQUIRED)
    target_include_directories(citoyen_llm PRIVATE ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_LIST_DIR}/host-stub)
endif()
