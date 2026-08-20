cmake_minimum_required(VERSION 3.22.1)
project("termuxlite_native")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -flto -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto -fvisibility=hidden -ffunction-sections -fdata-sections -fno-rtti")

if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8-a+crypto)
endif()

add_library(termuxlite_native SHARED
    src/main/cpp/termux_native_engine.cpp
    src/main/cpp/crypto/neon_sha256.cpp
    src/main/cpp/scanner/url_scanner.cpp
)

find_library(log-lib log)

target_link_libraries(termuxlite_native
    ${log-lib}
)
