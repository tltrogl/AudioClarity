package com.example.audioclarity

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A simple, thread-safe ring buffer for raw audio data (Shorts).
 */
class RingBuffer(val size: Int) {
    private val buffer = ShortArray(size)
    private var writePosition = 0
    private val lock = ReentrantLock()

    /**
     * The number of samples currently stored in the buffer.
     * Can be less than the total size if the buffer hasn't filled up yet.
     */
    @Volatile
    var availableSamples = 0
        private set

    /**
     * Writes data into the ring buffer, overwriting the oldest data if full.
     *
     * @param data The source array containing the audio data.
     * @param count The number of samples to write from the source array.
     */
    fun write(data: ShortArray, count: Int) {
        if (count > size) {
            throw IllegalArgumentException("Write count cannot be larger than the buffer size.")
        }

        lock.withLock {
            for (i in 0 until count) {
                buffer[writePosition] = data[i]
                writePosition = (writePosition + 1) % size
            }
            if (availableSamples < size) {
                availableSamples = minOf(size, availableSamples + count)
            }
        }
    }

    /**
     * Creates a snapshot of the current buffer data in chronological order.
     *
     * @return A new ShortArray containing the ordered audio data.
     */
    fun getOrderedSnapshot(): ShortArray {
        val snapshot = ShortArray(availableSamples)
        lock.withLock {
            val readPosition = if (availableSamples < size) 0 else writePosition
            for (i in 0 until availableSamples) {
                snapshot[i] = buffer[(readPosition + i) % size]
            }
        }
        return snapshot
    }
}
