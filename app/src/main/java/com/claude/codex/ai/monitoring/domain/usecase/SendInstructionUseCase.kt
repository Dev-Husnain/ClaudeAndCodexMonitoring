package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository

/** Trims and bounds an instruction before sending it; blank input is never sent. */
class SendInstructionUseCase(
    private val agentRepository: AgentRepository,
) {
    suspend operator fun invoke(sessionId: String, text: String): DeliveryStatus? {
        val instruction = text.trim()
        if (instruction.isEmpty()) return null
        return agentRepository.sendInstruction(sessionId, instruction.take(MAX_LENGTH))
    }

    companion object {
        const val MAX_LENGTH = 4_000
    }
}
