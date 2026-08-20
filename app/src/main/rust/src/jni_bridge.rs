#![allow(non_camel_case_types)]

use std::ffi::{c_char, c_void, CStr, CString};
use std::ptr;

pub type jint = i32;
pub type jsize = i32;
pub type jboolean = u8;
pub type jobject = *mut c_void;
pub type jclass = jobject;
pub type jstring = jobject;

pub const JNI_TRUE: jboolean = 1;
pub const JNI_FALSE: jboolean = 0;

pub type JNIEnv = *const JNINativeInterface;

#[repr(C)]
pub struct JNINativeInterface {
    pub reserved0: *mut c_void,
    pub reserved1: *mut c_void,
    pub reserved2: *mut c_void,
    pub reserved3: *mut c_void,

    pub _padding: [*const c_void; 163],

    pub new_string_utf: unsafe extern "C" fn(env: *mut JNIEnv, bytes: *const c_char) -> jstring,
    pub get_string_utf_length: unsafe extern "C" fn(env: *mut JNIEnv, string: jstring) -> jsize,
    pub get_string_utf_chars: unsafe extern "C" fn(
        env: *mut JNIEnv,
        string: jstring,
        is_copy: *mut jboolean,
    ) -> *const c_char,
    pub release_string_utf_chars: unsafe extern "C" fn(
        env: *mut JNIEnv,
        string: jstring,
        utf: *const c_char,
    ),
    pub _tail_padding: [*const c_void; 62],
}

pub struct JniEnvGuard<'a> {
    env: *mut JNIEnv,
    _marker: std::marker::PhantomData<&'a ()>,
}

impl<'a> JniEnvGuard<'a> {
    /// # Safety
    /// `env` must be a valid non-null JNIEnv pointer.
    pub unsafe fn from_raw(env: *mut JNIEnv) -> Option<Self> {
        if env.is_null() || (*env).is_null() {
            None
        } else {
            Some(Self {
                env,
                _marker: std::marker::PhantomData,
            })
        }
    }

    pub fn jstring_to_string(&self, jstr: jstring) -> Option<String> {
        if jstr.is_null() {
            return None;
        }
        unsafe {
            let table = *self.env;
            let raw = ((*table).get_string_utf_chars)(self.env, jstr, ptr::null_mut());
            if raw.is_null() {
                return None;
            }
            let cstr = CStr::from_ptr(raw);
            let result = cstr.to_string_lossy().into_owned();
            ((*table).release_string_utf_chars)(self.env, jstr, raw);
            Some(result)
        }
    }

    pub fn string_to_jstring(&self, s: &str) -> jstring {
        let Ok(c_str) = CString::new(s) else {
            return ptr::null_mut();
        };
        unsafe {
            let table = *self.env;
            ((*table).new_string_utf)(self.env, c_str.as_ptr())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_jni_offsets() {
        assert_eq!(std::mem::size_of::<JNINativeInterface>(), 1864);
        assert_eq!(std::mem::offset_of!(JNINativeInterface, new_string_utf), 1336);
        assert_eq!(std::mem::offset_of!(JNINativeInterface, get_string_utf_length), 1344);
        assert_eq!(std::mem::offset_of!(JNINativeInterface, get_string_utf_chars), 1352);
        assert_eq!(std::mem::offset_of!(JNINativeInterface, release_string_utf_chars), 1360);
    }
}
