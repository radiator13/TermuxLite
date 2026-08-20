cmake_minimum_required(VERSION 3.22.1)
project("termuxlite_native")

# Rust native engine integration for TermuxLite
set(RUST_MANIFEST "${CMAKE_CURRENT_SOURCE_DIR}/src/main/rust/Cargo.toml")
set(RUST_TARGET_DIR "${CMAKE_CURRENT_BINARY_DIR}/cargo_target")

if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CARGO_FLAGS "")
    set(CARGO_PROFILE "debug")
else()
    set(CARGO_FLAGS "--release")
    set(CARGO_PROFILE "release")
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CARGO_TARGET_TRIPLE "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CARGO_TARGET_TRIPLE "armv7-linux-androideabi")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(CARGO_TARGET_TRIPLE "x86_64-linux-android")
elseif(ANDROID_ABI STREQUAL "x86")
    set(CARGO_TARGET_TRIPLE "i686-linux-android")
else()
    set(CARGO_TARGET_TRIPLE "")
endif()

set(TARGET_OPT "")
if(CARGO_TARGET_TRIPLE)
    set(TARGET_OPT "--target" "${CARGO_TARGET_TRIPLE}")
    set(BUILT_SO "${RUST_TARGET_DIR}/${CARGO_TARGET_TRIPLE}/${CARGO_PROFILE}/libtermuxlite_native.so")
else()
    set(BUILT_SO "${RUST_TARGET_DIR}/${CARGO_PROFILE}/libtermuxlite_native.so")
endif()

add_custom_command(
    OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/libtermuxlite_native.so"
    COMMAND cargo build --manifest-path "${RUST_MANIFEST}" --target-dir "${RUST_TARGET_DIR}" ${TARGET_OPT} ${CARGO_FLAGS}
    COMMAND ${CMAKE_COMMAND} -E copy_if_different "${BUILT_SO}" "${CMAKE_CURRENT_BINARY_DIR}/libtermuxlite_native.so"
    DEPENDS
        "${RUST_MANIFEST}"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/rust/src/lib.rs"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/rust/src/crypto.rs"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/rust/src/scanner.rs"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/main/rust/src/jni_bridge.rs"
    COMMENT "Compiling TermuxLite Rust native acceleration engine..."
)

add_custom_target(termuxlite_native_cargo ALL
    DEPENDS "${CMAKE_CURRENT_BINARY_DIR}/libtermuxlite_native.so"
)

add_library(termuxlite_native SHARED IMPORTED GLOBAL)
set_target_properties(termuxlite_native PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_BINARY_DIR}/libtermuxlite_native.so"
)
add_dependencies(termuxlite_native termuxlite_native_cargo)
