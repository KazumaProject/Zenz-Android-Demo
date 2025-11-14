cmake_minimum_required(VERSION 3.22.1)
project(zenz_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# jniLibs に置いた .so を IMPORTED ライブラリとして扱う
# CMAKE_SOURCE_DIR = app/src/main/cpp
# jniLibs         = app/src/main/jniLibs
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

# JNI ブリッジ本体
add_library(zenz_bridge SHARED
        zenz_bridge.cpp
)

# llama.h / ggml*.h が置いてあるフォルダを include path に追加
# (ggml-backend.h, ggml-cpu.h, ggml.h, llama.h が src/main/cpp にある想定)
target_include_directories(zenz_bridge PRIVATE
        ${CMAKE_SOURCE_DIR}
)

# Android の log ライブラリ
find_library(log-lib log)

# Android の log ライブラリ + llama/ggml にリンク
target_link_libraries(zenz_bridge
        PRIVATE
        llama
        ggml
        ggml-base
        ggml-cpu
        ${log-lib}
)
