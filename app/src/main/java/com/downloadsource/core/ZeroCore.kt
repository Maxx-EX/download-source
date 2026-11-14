package com.downloadsource.core

object ZeroCore {
    init { System.loadLibrary("zerocore") }

    external fun mergeFiles(parts: Array<String>, output: String): Boolean
    external fun crc32(path: String): Long
    external fun version(): String
}
