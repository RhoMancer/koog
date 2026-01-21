package ai.koog.agents.features.mcp

import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport.StderrSeverity
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Creates a new stdio client transport for the specified process.
 */
public fun stdioClientTransport(process: Process): Transport {
    return StdioClientTransport(
        input = process.inputStream.asSource().buffered(),
        output = process.outputStream.asSink().buffered(),
        error = process.errorStream.asSource().buffered()
    ) { stderrLine ->
        when {
            stderrLine.contains("error", ignoreCase = true) -> StderrSeverity.FATAL
            stderrLine.contains("warning", ignoreCase = true) -> StderrSeverity.WARNING
            else -> StderrSeverity.INFO
        }
    }
}

