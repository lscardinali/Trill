package com.lscardinali.trill.audio

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.random.Random

/**
 * Minimal Ogg Opus muxer (RFC 7845). Android's MediaMuxer writes one Ogg page per 20 ms packet
 * (~28 bytes of overhead each, ~1.4 KB/s); packing ~1 s of packets per page makes tiny jingles
 * roughly 40% smaller at low bitrates.
 */
class OggOpusWriter(
    private val out: OutputStream,
    channels: Int,
    private val preSkip: Int,
    vendor: String = "Trill",
) {
    private val serial = Random.nextInt()
    private var sequence = 0
    private var granule = 0L
    private val packets = ArrayList<ByteArray>()
    private var segments = 0

    init {
        val head = ByteArrayOutputStream().apply {
            write("OpusHead".toByteArray())
            write(1)
            write(channels)
            le16(preSkip)
            le32(48_000)
            le16(0)
            write(0)
        }.toByteArray()
        writePage(listOf(head), 0, BOS)
        val v = vendor.toByteArray()
        val tags = ByteArrayOutputStream().apply {
            write("OpusTags".toByteArray())
            le32(v.size); write(v)
            le32(0)
        }.toByteArray()
        writePage(listOf(tags), 0, 0)
    }

    /** @param totalLimit final granule cap (preSkip + real PCM length) so players trim padding. */
    fun writePacket(packet: ByteArray, totalLimit: Long) {
        val segs = packet.size / 255 + 1
        if (segments + segs > 255) flush(0, totalLimit)
        packets += packet
        segments += segs
        granule += samplesIn(packet)
        if (granule - lastFlushGranule >= 48_000) flush(0, totalLimit)
    }

    private var lastFlushGranule = 0L

    fun finish(totalLimit: Long) {
        flush(EOS, totalLimit, force = true)
        out.flush()
    }

    private fun flush(flags: Int, totalLimit: Long, force: Boolean = false) {
        if (packets.isEmpty() && !force) return
        val g = (preSkip + granule).coerceAtMost(totalLimit)
        writePage(packets.toList(), if (flags and EOS != 0) totalLimit else g, flags)
        packets.clear()
        segments = 0
        lastFlushGranule = granule
    }

    private fun writePage(data: List<ByteArray>, granulePos: Long, flags: Int) {
        val table = ByteArrayOutputStream()
        for (p in data) {
            repeat(p.size / 255) { table.write(255) }
            table.write(p.size % 255)
        }
        val seg = table.toByteArray()
        val page = ByteArrayOutputStream().apply {
            write("OggS".toByteArray())
            write(0)
            write(flags)
            le64(granulePos)
            le32(serial)
            le32(sequence++)
            le32(0) // CRC placeholder
            write(seg.size)
            write(seg)
            data.forEach { write(it) }
        }.toByteArray()
        val crc = crc(page)
        page[22] = crc.toByte(); page[23] = (crc ushr 8).toByte(); page[24] = (crc ushr 16).toByte(); page[25] = (crc ushr 24).toByte()
        out.write(page)
    }

    companion object {
        private const val BOS = 0x02
        private const val EOS = 0x04

        private val CRC_TABLE = IntArray(256) { i ->
            var r = i shl 24
            repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
            r
        }

        private fun crc(b: ByteArray): Int {
            var c = 0
            for (x in b) c = (c shl 8) xor CRC_TABLE[((c ushr 24) xor (x.toInt() and 0xFF)) and 0xFF]
            return c
        }

        /** Samples (at 48 kHz) in one Opus packet, from its TOC byte (RFC 6716 §3.1). */
        fun samplesIn(p: ByteArray): Int {
            if (p.isEmpty()) return 0
            val toc = p[0].toInt() and 0xFF
            val config = toc shr 3
            val frameSize = when {
                config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3]
                config < 16 -> intArrayOf(480, 960)[config and 1]
                else -> intArrayOf(120, 240, 480, 960)[config and 3]
            }
            val frames = when (toc and 3) {
                0 -> 1
                1, 2 -> 2
                else -> if (p.size > 1) p[1].toInt() and 0x3F else 1
            }
            return frameSize * frames
        }

        private fun ByteArrayOutputStream.le16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
        private fun ByteArrayOutputStream.le32(v: Int) { for (i in 0 until 4) write((v shr (8 * i)) and 0xFF) }
        private fun ByteArrayOutputStream.le64(v: Long) { for (i in 0 until 8) write(((v shr (8 * i)) and 0xFF).toInt()) }
    }
}
