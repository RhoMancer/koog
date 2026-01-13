package ai.koog.integration.tests.base;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Base class for Koog Java integration tests.
 * Provides utilities for bridging Kotlin coroutines to Java.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
public abstract class KoogJavaTestBase {

    protected static final long DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * Execute a Kotlin suspend function synchronously (blocking).
     * Use for integration tests only—not production code.
     *
     * @param suspendFunction The suspend function to execute
     * @param <T> Return type
     * @return The result of the suspend function
     */
    @SuppressWarnings("unchecked")
    protected <T> T runBlocking(SuspendFunction<T> suspendFunction) {
        try {
            return (T) BuildersKt.runBlocking(
                EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> suspendFunction.invoke(continuation)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }

    /**
     * Functional interface for suspend functions.
     * Allows passing Kotlin suspend functions from Java.
     */
    @FunctionalInterface
    public interface SuspendFunction<T> {
        Object invoke(Continuation<? super T> continuation);
    }

    /**
     * Check if an environment variable is present.
     * Throws IllegalStateException if not found.
     */
    protected void assertApiKeyPresent(String envVar) {
        String key = System.getenv(envVar);
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(envVar + " environment variable not set");
        }
    }

    /**
     * Check if an environment variable is present (non-throwing).
     * @return true if the environment variable is set and non-empty
     */
    protected boolean isApiKeyPresent(String envVar) {
        String key = System.getenv(envVar);
        return key != null && !key.isEmpty();
    }
}
