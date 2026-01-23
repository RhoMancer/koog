package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random

/**
 * A tool that provides a random number using the passed seed.
 */
public class RandomNumberTool : Tool<RandomNumberTool.Args, Int>(
    argsSerializer = Args.serializer(),
    resultSerializer = Int.serializer(),
    name = "random_number",
    description = "Generates a random number"
) {

    private val _results: MutableList<Int> = mutableListOf()

    /**
     * A read-only list of all generated random numbers.
     */
    public val results: List<Int> get() = _results.toList()

    /**
     * The last generated random number.
     */
    public val last: Int?
        get() = _results.lastOrNull()

    private val logger = KotlinLogging.logger {}

    /**
     * Represents the arguments for the RandomNumberTool.
     *
     * @property seed The seed for the random number generator.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription("The seed for the random number generator")
        val seed: Int? = null
    )

    override suspend fun execute(args: Args): Int {
        val seed = args.seed
        val random = if (seed == null) Random else Random(seed)

        val result = random.nextInt().also { number ->
            logger.info { "Generated random number: $number [seed=$seed]" }
            _results.add(number)
        }

        return result
    }

    /**
     * Clears the list of generated random numbers.
     */
    public fun clear() {
        _results.clear()
    }
}
