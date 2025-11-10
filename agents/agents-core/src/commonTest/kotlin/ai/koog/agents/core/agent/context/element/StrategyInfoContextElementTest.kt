package ai.koog.agents.core.agent.context.element

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class StrategyInfoContextElementTest {

    @Test
    fun testContextElementCreation() {
        val element = StrategyInfoContextElement(
            id = "strategy-id",
            parentId = "parent-strategy-id",
            strategyName = "TestStrategy",
        )

        assertEquals("strategy-id", element.id)
        assertEquals("parent-strategy-id", element.parentId)
        assertEquals("TestStrategy", element.strategyName)
        assertEquals(StrategyInfoContextElement.Key, element.key)
    }

    @Test
    fun testContextElementEquality() {
        val element1 = StrategyInfoContextElement(
            id = "id1",
            parentId = "parent1",
            strategyName = "StrategyA",
        )

        val element2 = StrategyInfoContextElement(
            id = "id1",
            parentId = "parent1",
            strategyName = "StrategyA",
        )

        val element3 = StrategyInfoContextElement(
            id = "id2",
            parentId = "parent2",
            strategyName = "StrategyB",
        )

        assertEquals(element1, element2)
        assertEquals(element1.hashCode(), element2.hashCode())
        assertNotEquals(element1, element3)
    }

    @Test
    fun testGetExistingElementFromContext() = runTest {
        val element = StrategyInfoContextElement(
            id = "id",
            parentId = "parent",
            strategyName = "MyStrategy",
        )

        withContext(element) {
            val retrieved = getStrategyInfoElement()
            assertNotNull(retrieved)
            assertEquals(element, retrieved)
        }
    }

    @Test
    fun testGetNonExistingElementFromContext() = runTest {
        val retrieved = getStrategyInfoElement()
        assertEquals(null, retrieved)
    }

    @Test
    fun testGetElementOrThrow() = runTest {
        val element = StrategyInfoContextElement(
            id = "id",
            parentId = "parent",
            strategyName = "MyStrategy",
        )

        withContext(element) {
            val retrieved = getStrategyInfoElementOrThrow()
            assertEquals(element, retrieved)
        }

        assertFailsWith<IllegalStateException> {
            getStrategyInfoElementOrThrow()
        }
    }
}
