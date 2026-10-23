package org.matrix.rustsdk

import android.content.Context

/**
 * JNI bridge expected by libmatrix_sdk_ffi.so.
 *
 * The Rust side exports Java_org_matrix_rustsdk_Android_init and panics with
 * "The JavaVM hasn't been initialized" until this is called before
 * initPlatform. The native library itself comes from the matrix-rust-sdk AAR.
 */
object Android {
    init {
        System.loadLibrary("matrix_sdk_ffi")
    }

    external fun init(context: Context)
}
