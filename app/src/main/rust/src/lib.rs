pub mod crypto;
pub mod scanner;
pub mod jni_bridge;

use jni_bridge::{jboolean, jclass, jint, jstring, JniEnvGuard, JNIEnv, JNI_TRUE};
use std::ptr;

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_termux_lite_NativeBridge_nativeIsAvailable(
    _env: *mut JNIEnv,
    _class: jclass,
) -> jboolean {
    JNI_TRUE
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_termux_lite_NativeBridge_nativeSha256File(
    env: *mut JNIEnv,
    _class: jclass,
    file_path_jstr: jstring,
) -> jstring {
    let Some(guard) = (unsafe { JniEnvGuard::from_raw(env) }) else {
        return ptr::null_mut();
    };

    let Some(path) = guard.jstring_to_string(file_path_jstr) else {
        return ptr::null_mut();
    };

    match crypto::sha256_file(&path) {
        Some(hash) => guard.string_to_jstring(&hash),
        None => ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_termux_lite_NativeBridge_nativeFindUrlAt(
    env: *mut JNIEnv,
    _class: jclass,
    line_jstr: jstring,
    col: jint,
) -> jstring {
    let Some(guard) = (unsafe { JniEnvGuard::from_raw(env) }) else {
        return ptr::null_mut();
    };

    let Some(line) = guard.jstring_to_string(line_jstr) else {
        return ptr::null_mut();
    };

    let col_idx = col.max(0) as usize;
    match scanner::find_url_at(&line, col_idx) {
        Some(url) => guard.string_to_jstring(&url),
        None => ptr::null_mut(),
    }
}
