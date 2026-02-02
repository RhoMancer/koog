package ai.koog.agents.core.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReentrantRWLockTest {

    // ==========================================
    // Basic functionality tests (same as RWLock)
    // ==========================================

    @Test
    fun testMultipleReaders() = runTest {
        val rwLock = ReentrantRWLock()
        var counter = 0

        // Launch multiple readers concurrently
        val jobs = List(5) {
            launch {
                rwLock.withReadLock {
                    counter++
                    delay(10) // Simulate some work
                    counter--
                }
            }
        }

        // Wait for all readers to complete
        jobs.joinAll()

        // Counter should be 0 after all readers have completed
        assertEquals(0, counter)
    }

    @Test
    fun testExclusiveWriter() = runTest {
        val rwLock = ReentrantRWLock()
        var sharedResource = 0
        var writerActive = false

        // Launch a writer
        val writerJob = launch {
            rwLock.withWriteLock {
                writerActive = true
                sharedResource = 1
                delay(50) // Simulate some work
                sharedResource = 2
                writerActive = false
            }
        }

        // Launch a reader that should wait for the writer to complete
        val readerJob = async {
            rwLock.withReadLock {
                assertFalse(writerActive, "Reader should not be active while writer is active")
                return@withReadLock sharedResource
            }
        }

        writerJob.join()
        val result = readerJob.await()

        // Reader should see the final value written by the writer
        assertEquals(2, result)
    }

    @Test
    fun testReaderBlocksWriter() = runTest {
        val rwLock = ReentrantRWLock()
        var readerActive = false
        var writerExecuted = false

        // Launch a reader
        val readerJob = launch {
            rwLock.withReadLock {
                readerActive = true
                delay(50) // Hold the read lock for a while
                readerActive = false
            }
        }

        // Give the reader time to acquire the lock
        while (!readerActive) delay(10)

        // Launch a writer that should wait for the reader to complete
        val writerJob = launch {
            rwLock.withWriteLock {
                assertFalse(readerActive, "Writer should not be active while reader is active")
                writerExecuted = true
            }
        }

        readerJob.join()
        writerJob.join()

        assertTrue(writerExecuted, "Writer should have executed after reader completed")
    }

    @Test
    fun testMultipleReadersOneWriter() = runTest {
        val rwLock = ReentrantRWLock()
        var activeReaders = 0
        var writerActive = false
        var maxActiveReaders = 0

        // Launch multiple readers
        val readerJobs = List(3) {
            launch {
                rwLock.withReadLock {
                    activeReaders++
                    maxActiveReaders = maxOf(maxActiveReaders, activeReaders)
                    assertFalse(writerActive, "Reader should not be active while writer is active")
                    delay(30) // Simulate some work
                    activeReaders--
                }
            }
        }

        // Give readers time to acquire locks
        delay(10)

        // Launch a writer
        val writerJob = launch {
            rwLock.withWriteLock {
                writerActive = true
                assertEquals(0, activeReaders, "No readers should be active during write lock")
                delay(30) // Simulate some work
                writerActive = false
            }
        }

        readerJobs.joinAll()
        writerJob.join()

        assertTrue(maxActiveReaders > 1, "Multiple readers should have been active concurrently")
        assertEquals(0, activeReaders, "All readers should have completed")
    }

    @Test
    fun testExceptionHandling() = runTest {
        val rwLock = ReentrantRWLock()
        var lockReleased = false

        try {
            rwLock.withReadLock {
                throw RuntimeException("Test exception")
            }
        } catch (_: RuntimeException) {
            // Expected exception
        }

        // Verify that the lock was released by acquiring a write lock
        rwLock.withWriteLock {
            lockReleased = true
        }

        assertTrue(lockReleased, "Lock should be released even if an exception occurs")
    }

    // ==========================================
    // Reentrancy tests
    // ==========================================

    @Test
    fun testReentrantWriteLock() = runTest {
        val rwLock = ReentrantRWLock()
        var innerExecuted = false
        var outerExecuted = false

        rwLock.withWriteLock {
            outerExecuted = true
            // Nested write lock should not deadlock
            rwLock.withWriteLock {
                innerExecuted = true
            }
        }

        assertTrue(outerExecuted, "Outer write lock block should have executed")
        assertTrue(innerExecuted, "Inner write lock block should have executed (reentrant)")
    }

    @Test
    fun testReentrantReadLock() = runTest {
        val rwLock = ReentrantRWLock()
        var innerExecuted = false
        var outerExecuted = false

        rwLock.withReadLock {
            outerExecuted = true
            // Nested read lock should not deadlock
            rwLock.withReadLock {
                innerExecuted = true
            }
        }

        assertTrue(outerExecuted, "Outer read lock block should have executed")
        assertTrue(innerExecuted, "Inner read lock block should have executed (reentrant)")
    }

    @Test
    fun testReadLockInsideWriteLock() = runTest {
        val rwLock = ReentrantRWLock()
        var innerExecuted = false
        var outerExecuted = false

        rwLock.withWriteLock {
            outerExecuted = true
            // Read lock inside write lock should work (write implies read)
            rwLock.withReadLock {
                innerExecuted = true
            }
        }

        assertTrue(outerExecuted, "Outer write lock block should have executed")
        assertTrue(innerExecuted, "Inner read lock block should have executed (write implies read)")
    }

    @Test
    fun testWriteLockInsideReadLockThrows() = runTest {
        val rwLock = ReentrantRWLock()

        assertFailsWith<IllegalStateException> {
            rwLock.withReadLock {
                // Write lock inside read lock should throw
                rwLock.withWriteLock {
                    // Should not reach here
                }
            }
        }
    }

    @Test
    fun testDeeplyNestedWriteLocks() = runTest {
        val rwLock = ReentrantRWLock()
        var depth = 0
        var maxDepth = 0

        suspend fun nestedWrite(level: Int) {
            if (level > 5) return
            rwLock.withWriteLock {
                depth++
                maxDepth = maxOf(maxDepth, depth)
                nestedWrite(level + 1)
                depth--
            }
        }

        nestedWrite(1)

        assertEquals(5, maxDepth, "Should have reached depth 5 with nested write locks")
    }

    @Test
    fun testDeeplyNestedReadLocks() = runTest {
        val rwLock = ReentrantRWLock()
        var depth = 0
        var maxDepth = 0

        suspend fun nestedRead(level: Int) {
            if (level > 5) return
            rwLock.withReadLock {
                depth++
                maxDepth = maxOf(maxDepth, depth)
                nestedRead(level + 1)
                depth--
            }
        }

        nestedRead(1)

        assertEquals(5, maxDepth, "Should have reached depth 5 with nested read locks")
    }

    @Test
    fun testMixedNestedLocksWriteFirst() = runTest {
        val rwLock = ReentrantRWLock()
        val executionOrder = mutableListOf<String>()

        rwLock.withWriteLock {
            executionOrder.add("write1-start")
            rwLock.withReadLock {
                executionOrder.add("read1-start")
                rwLock.withWriteLock {
                    executionOrder.add("write2")
                }
                executionOrder.add("read1-end")
            }
            executionOrder.add("write1-end")
        }

        assertEquals(
            listOf("write1-start", "read1-start", "write2", "read1-end", "write1-end"),
            executionOrder,
            "Mixed nested locks should execute in correct order"
        )
    }

    @Test
    fun testReentrantWriteLockWithReturnValue() = runTest {
        val rwLock = ReentrantRWLock()

        val result = rwLock.withWriteLock {
            val inner = rwLock.withWriteLock {
                42
            }
            inner * 2
        }

        assertEquals(84, result, "Return values should propagate correctly through nested locks")
    }

    @Test
    fun testReentrantReadLockWithReturnValue() = runTest {
        val rwLock = ReentrantRWLock()

        val result = rwLock.withReadLock {
            val inner = rwLock.withReadLock {
                "hello"
            }
            inner.uppercase()
        }

        assertEquals("HELLO", result, "Return values should propagate correctly through nested read locks")
    }

    @Test
    fun testExceptionInNestedWriteLock() = runTest {
        val rwLock = ReentrantRWLock()
        var lockReleased = false

        try {
            rwLock.withWriteLock {
                rwLock.withWriteLock {
                    throw RuntimeException("Nested exception")
                }
            }
        } catch (_: RuntimeException) {
            // Expected
        }

        // Verify lock was released
        rwLock.withWriteLock {
            lockReleased = true
        }

        assertTrue(lockReleased, "Lock should be released after exception in nested write lock")
    }

    @Test
    fun testExceptionInNestedReadLock() = runTest {
        val rwLock = ReentrantRWLock()
        var lockReleased = false

        try {
            rwLock.withReadLock {
                rwLock.withReadLock {
                    throw RuntimeException("Nested exception")
                }
            }
        } catch (_: RuntimeException) {
            // Expected
        }

        // Verify lock was released
        rwLock.withWriteLock {
            lockReleased = true
        }

        assertTrue(lockReleased, "Lock should be released after exception in nested read lock")
    }

    // ==========================================
    // Concurrency with reentrancy tests
    // ==========================================

    @Test
    fun testConcurrentReentrantWriters() = runTest {
        val rwLock = ReentrantRWLock()
        var sharedCounter = 0
        val iterations = 100

        val jobs = List(5) {
            launch {
                repeat(iterations) {
                    rwLock.withWriteLock {
                        val current = sharedCounter
                        rwLock.withWriteLock {
                            // Nested write should see same state
                            assertEquals(current, sharedCounter)
                            sharedCounter++
                        }
                    }
                }
            }
        }

        jobs.joinAll()

        assertEquals(5 * iterations, sharedCounter, "All increments should be counted")
    }

    @Test
    fun testConcurrentReentrantReaders() = runTest {
        val rwLock = ReentrantRWLock()
        val sharedValue = 42
        var readCount = 0
        val readCountLock = ReentrantRWLock()

        val jobs = List(10) {
            launch {
                rwLock.withReadLock {
                    val outer = sharedValue
                    rwLock.withReadLock {
                        // Nested read should see same value
                        assertEquals(outer, sharedValue)
                        readCountLock.withWriteLock {
                            readCount++
                        }
                    }
                }
            }
        }

        jobs.joinAll()

        assertEquals(10, readCount, "All readers should have completed")
    }

    // ==========================================
    // Edge cases
    // ==========================================

    @Test
    fun testEmptyBlocks() = runTest {
        val rwLock = ReentrantRWLock()

        rwLock.withReadLock { }
        rwLock.withWriteLock { }
        rwLock.withReadLock {
            rwLock.withReadLock { }
        }
        rwLock.withWriteLock {
            rwLock.withWriteLock { }
        }

        // Should complete without issues
        assertTrue(true)
    }

    @Test
    fun testIndependentLocks() = runTest {
        val lock1 = ReentrantRWLock()
        val lock2 = ReentrantRWLock()
        var executed = false

        // Different locks should not interfere
        lock1.withWriteLock {
            lock2.withWriteLock {
                lock1.withReadLock {
                    lock2.withReadLock {
                        executed = true
                    }
                }
            }
        }

        assertTrue(executed, "Independent locks should not interfere with each other")
    }
}
