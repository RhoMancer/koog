package ai.koog.agents.core.agent.context.element

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SubgraphInfoContextElementTest {

    @Test
    fun testContextElementCreation() {
        val element = SubgraphInfoContextElement(
            id = "subgraph-id",
            parentId = "parent-subgraph-id",
            subgraphName = "TestSubgraph",
            input = "input-value",
            inputType = typeOf<String>(),
        )

        assertEquals("subgraph-id", element.id)
        assertEquals("parent-subgraph-id", element.parentId)
        assertEquals("TestSubgraph", element.subgraphName)
        assertEquals("input-value", element.input)
        assertEquals(typeOf<String>(), element.inputType)
        assertEquals(SubgraphInfoContextElement.Key, element.key)
    }

    @Test
    fun testContextElementEquality() {
        val element1 = SubgraphInfoContextElement(
            id = "id1",
            parentId = "parent1",
            subgraphName = "SubgraphA",
            input = 123,
            inputType = typeOf<Int>(),
        )

        val element2 = SubgraphInfoContextElement(
            id = "id1",
            parentId = "parent1",
            subgraphName = "SubgraphA",
            input = 123,
            inputType = typeOf<Int>(),
        )

        val element3 = SubgraphInfoContextElement(
            id = "id2",
            parentId = "parent2",
            subgraphName = "SubgraphB",
            input = "different",
            inputType = typeOf<String>(),
        )

        assertEquals(element1, element2)
        assertEquals(element1.hashCode(), element2.hashCode())
        assertNotEquals(element1, element3)
    }

    @Test
    fun testGetExistingElementFromContext() = runTest {
        val element = SubgraphInfoContextElement(
            id = "id",
            parentId = "parent",
            subgraphName = "MySubgraph",
            input = listOf(1, 2, 3),
            inputType = typeOf<List<Int>>(),
        )

        withContext(element) {
            val retrieved = getSubgraphInfoElement()
            assertNotNull(retrieved)
            assertEquals(element, retrieved)
        }
    }

    @Test
    fun testGetNonExistingElementFromContext() = runTest {
        val retrieved = getSubgraphInfoElement()
        assertEquals(null, retrieved)
    }

    @Test
    fun testGetElementOrThrow() = runTest {
        val element = SubgraphInfoContextElement(
            id = "id",
            parentId = "parent",
            subgraphName = "MySubgraph",
            input = null,
            inputType = typeOf<String?>(),
        )

        withContext(element) {
            val retrieved = getSubgraphInfoElementOrThrow()
            assertEquals(element, retrieved)
        }

        assertFailsWith<IllegalStateException> {
            getSubgraphInfoElementOrThrow()
        }
    }
}
