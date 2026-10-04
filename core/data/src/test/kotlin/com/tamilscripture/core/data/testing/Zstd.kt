package com.tamilscripture.core.data.testing

import java.io.ByteArrayOutputStream

/**
 * A valid zstd frame made of raw (stored) blocks: what pack-build would publish, without
 * a compressor in the test classpath. The app's Rust decoder reads it like any other frame.
 */
object Zstd {
    private const val BLOCK = 128 * 1024

    fun stored(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())) // magic
        out.write(0x00) // frame header: no content size, no checksum, no dictionary
        out.write(7 shl 3) // window 1 << (10 + 7) = 128 KiB
        var at = 0
        do {
            val n = minOf(BLOCK, data.size - at)
            val last = at + n >= data.size
            val header = (if (last) 1 else 0) or (n shl 3) // block type 0: raw
            out.write(header and 0xFF)
            out.write((header shr 8) and 0xFF)
            out.write((header shr 16) and 0xFF)
            out.write(data, at, n)
            at += n
        } while (!last)
        return out.toByteArray()
    }
}
