package com.downloadsource.core

object ZeroCore {
    init { System.loadLibrary("zerocore") }
    
    // 预分配空间
    external fun fallocate(path: String, size: Long): Boolean
    // 合并分片
    external fun mergeFiles(parts: Array<String>, output: String): Boolean
    // 计算 CRC32
    external fun crc32(path: String): Long
}
