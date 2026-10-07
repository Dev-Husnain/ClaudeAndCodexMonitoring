package com.claude.codex.ai.monitoring.cli

import kotlin.system.exitProcess

private val SESSION_ID = Regex("[A-Za-z0-9-]{8,64}")

private const val VERSION = "1.0.9"

private val USAGE = """
    agentmon $VERSION: run Claude Code so AgentMon can show its terminal and type into it from your phone.

    Usage:
      agentmon claude [claude arguments...]   Start Claude Code through AgentMon (arguments are passed on)
      agentmon --version

    AgentMon itself may start it as `agentmon --session <id> claude ...` when you start Claude from your phone.

    The AgentMon desktop agent should be running; without it Claude still runs normally.
    Set AGENTMON_CLAUDE to the full path of claude if it is not on PATH.
""".trimIndent()

fun main(args: Array<String>) {
    // Set by the desktop agent when the phone started this terminal, so the phone can open it at once.
    val sessionId = args.takeIf { it.size >= 2 && it[0] == "--session" }?.get(1)?.takeIf { SESSION_ID.matches(it) }
    val rest = if (args.firstOrNull() == "--session") args.drop(2) else args.toList()
    when (rest.firstOrNull()) {
        "claude" -> exitProcess(WrapperSession(rest.drop(1), sessionId).run())
        "--version", "-v" -> println("agentmon $VERSION")
        else -> {
            println(USAGE)
            if (rest.isNotEmpty() && rest[0] != "--help" && rest[0] != "-h") exitProcess(2)
        }
    }
}
