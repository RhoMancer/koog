package ai.koog.agents.core.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * A KMP reentrant read-write lock implementation that allows concurrent read access
 * but ensures exclusive write access, with support for nested lock acquisition.
 *
 * This implementation uses [Mutex] to coordinate access for both readers and writers,
 * and coroutine context elements to track lock ownership for reentrancy.
 *
 * Supported nested lock acquisition patterns:
 * - Write lock inside write lock: allowed (reentrant)
 * - Read lock inside read lock: allowed (reentrant)
 * - Read lock inside write lock: allowed (write implies read)
 * - Write lock inside read lock: NOT allowed (throws [IllegalStateException])
 */
internal class ReentrantRWLock {
    private val writeMutex = Mutex()
    private var readersCount = 0
    private val readersCountMutex = Mutex()

    private val contextKey = ReentrantRWLockContextKey(this)

    /**
     * Executes the given [block] while holding the read lock.
     *
     * Multiple coroutines can hold the read lock simultaneously.
     * If the current coroutine already holds a read or write lock, the block
     * is executed without acquiring additional locks (reentrant behavior).
     *
     * @param block The suspending block to execute while holding the read lock.
     * @return The result of the block execution.
     */
    suspend fun <T> withReadLock(block: suspend () -> T): T {
        val currentContext = coroutineContext[contextKey]

        // If we already hold a write lock, we can read without additional locking
        if (currentContext?.holdsWriteLock == true) {
            return block()
        }

        // If we already hold a read lock, just execute the block
        if (currentContext?.holdsReadLock == true) {
            return block()
        }

        // Acquire read lock
        readersCountMutex.withLock {
            if (++readersCount == 1) {
                writeMutex.lock()
            }
        }

        return try {
            withContext(ReentrantRWLockContextElement(contextKey, holdsReadLock = true, holdsWriteLock = false)) {
                block()
            }
        } finally {
            readersCountMutex.withLock {
                if (--readersCount == 0) {
                    writeMutex.unlock()
                }
            }
        }
    }

    /**
     * Executes the given [block] while holding the write lock.
     *
     * Only one coroutine can hold the write lock at a time, and no readers
     * can hold the read lock while the write lock is held.
     *
     * If the current coroutine already holds a write lock, the block is executed
     * without acquiring additional locks (reentrant behavior).
     *
     * @param block The suspending block to execute while holding the write lock.
     * @return The result of the block execution.
     * @throws IllegalStateException if the current coroutine holds a read lock
     *         and attempts to acquire a write lock (lock upgrade is not supported).
     */
    suspend fun <T> withWriteLock(block: suspend () -> T): T {
        val currentContext = coroutineContext[contextKey]

        // If we already hold a write lock, just execute (reentrant)
        if (currentContext?.holdsWriteLock == true) {
            return block()
        }

        // If we hold a read lock and try to upgrade to write, this would deadlock
        if (currentContext?.holdsReadLock == true) {
            throw IllegalStateException(
                "Cannot acquire write lock while holding read lock - would cause deadlock. " +
                    "Lock upgrade from read to write is not supported."
            )
        }

        return writeMutex.withLock {
            withContext(ReentrantRWLockContextElement(contextKey, holdsReadLock = false, holdsWriteLock = true)) {
                block()
            }
        }
    }
}

private data class ReentrantRWLockContextKey(
    val lock: ReentrantRWLock
) : CoroutineContext.Key<ReentrantRWLockContextElement>

private class ReentrantRWLockContextElement(
    override val key: ReentrantRWLockContextKey,
    val holdsReadLock: Boolean,
    val holdsWriteLock: Boolean
) : CoroutineContext.Element
