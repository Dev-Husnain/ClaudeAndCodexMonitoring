package com.claude.codex.ai.monitoring.cli

import kotlin.system.exitProcess

private const val VERSION = "1.0.3"

private val USAGE = """
    agentmon $VERSION: run Claude Code so AgentMon can show its terminal and type into it from your phone.

    Usage:
      agentmon claude [claude arguments...]   Start Claude Code through AgentMon (arguments are passed on)
      agentmon --version

    The AgentMon desktop agent should be running; without it Claude still runs normally.
    Set AGENTMON_CLAUDE to the full path of claude if it is not on PATH.
""".trimIndent()

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "claude" -> exitProcess(WrapperSession(args.drop(1)).run())
        "--version", "-v" -> println("agentmon $VERSION")
        else -> {
            println(USAGE)
            if (args.isNotEmpty() && args[0] != "--help" && args[0] != "-h") exitProcess(2)
        }
    }
}
