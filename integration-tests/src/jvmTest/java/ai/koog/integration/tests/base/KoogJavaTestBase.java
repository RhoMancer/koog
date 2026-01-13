package ai.koog.integration.tests.base;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * Base class for Koog Java integration tests.
 * Provides utilities for bridging Kotlin coroutines to Java.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
public abstract class KoogJavaTestBase {

    /**
     * Execute a Kotlin suspend function synchronously (blocking).
     * Use for integration tests only — not production code.
     *
     * @param suspendFunction The suspend function to execute
     * @param <T>             Return type
     * @return The result of the suspend function
     */
    @SuppressWarnings("unchecked")
    protected <T> T runBlocking(SuspendFunction<T> suspendFunction) {
        try {
            return BuildersKt.runBlocking(
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
}
