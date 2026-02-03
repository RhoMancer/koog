package ai.koog.agents.core.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A KMP read-write lock implementation that allows concurrent read access but ensures exclusive write access.
 *
 * This implementation uses `kotlinx.coroutines.sync.Mutex` to coordinate access for both readers and writers.
 */
internal class RWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    /**
     * CAVEAT: allows writer starvation: new readers can continuously acquire the lock while a writer is waiting.
     * If there's a steady stream of read requests, writers may wait indefinitely.
     */
    suspend fun <T> withReadLock(block: suspend () -> T): T {
        readersCountMutex.withLock {
            if (++readersCount == 1) {
                writeMutex.lock()
            }
        }

        return try {
            block()
        } finally {
            readersCountMutex.withLock {
                if (--readersCount == 0) {
                    writeMutex.unlock()
                }
            }
        }
    }

    /**
     * CAVEAT: uses kotlinx.coroutines.sync.Mutex which is not reentrant.
     * When the same coroutine tries to acquire the mutex it already holds, it blocks forever waiting for itself to release it.
     */
    suspend fun <T> withWriteLock(block: suspend () -> T): T {
        writeMutex.withLock {
            return block()
        }
    }
}
