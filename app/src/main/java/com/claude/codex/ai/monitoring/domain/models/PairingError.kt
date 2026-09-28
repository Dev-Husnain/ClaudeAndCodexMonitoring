package com.claude.codex.ai.monitoring.domain.models

/** Why pairing did not complete. Each case maps to a specific message on the Pair screen. */
sealed class PairingError : Exception() {
    /** Not an AgentMon pairing code. */
    class InvalidCode : PairingError()

    /** The code points at a plain-HTTP address that is not loopback. */
    class InsecureAddress : PairingError()

    class InvalidName : PairingError()

    class Rejected : PairingError()

    /** The code expired or was already used; create a new one on the computer. */
    class Expired : PairingError()

    class TimedOut : PairingError()

    class RateLimited : PairingError()

    /** The computer answered with a key that does not match the QR fingerprint. */
    class DesktopMismatch : PairingError()

    class Network(override val cause: Throwable? = null) : PairingError()
}
