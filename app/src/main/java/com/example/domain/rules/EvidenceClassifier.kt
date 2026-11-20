package com.example.domain.rules

import com.example.model.ConfirmationStatus
import com.example.model.EvidenceStatus

/**
 * Pure deterministic rules governing evidence labels and valid transitions.
 *
 * Core non-negotiable invariants:
 * 1. AI extraction NEVER sets SOURCE_MATCHED or SOURCE_VERIFIED_SIMULATED.
 * 2. AI-read fields become DOCUMENT_EXTRACTED ONLY after explicit worker confirmation.
 * 3. DISPUTED overrides every positive evidence tier until explicitly resolved.
 */
object EvidenceClassifier {

    /**
     * Determines the evidence status when a worker manually creates a record without a document.
     */
    fun forSelfDeclaredEntry(): EvidenceStatus = EvidenceStatus.SELF_DECLARED

    /**
     * Determines the evidence status after a worker reviews and explicitly confirms AI-read fields.
     * Throws IllegalArgumentException if workerConfirmed is false.
     */
    fun forWorkerConfirmedDocument(workerConfirmed: Boolean): EvidenceStatus {
        require(workerConfirmed) {
            "AI-extracted values must be explicitly confirmed by the worker before saving."
        }
        return EvidenceStatus.DOCUMENT_EXTRACTED
    }

    /**
     * Determines the evidence status for a simulated platform/API import (Rule 7).
     * Always carries the word "Simulated" in its display label.
     */
    fun forSimulatedApiImport(): EvidenceStatus = EvidenceStatus.SOURCE_VERIFIED_SIMULATED

    /**
     * Computes the resulting evidence status from all deterministic signals attached to a record.
     * Rule 7: Client confirmation updates relationship/payment status, NOT the evidence label
     * (except when disputed). Simulated import sets SOURCE_VERIFIED_SIMULATED.
     */
    fun classifyFromSignals(
        hasConfirmedDocument: Boolean,
        hasConfirmedBankMatch: Boolean,
        confirmationStatus: ConfirmationStatus?,
        explicitlyDisputed: Boolean,
        simulatedApi: Boolean = false
    ): EvidenceStatus {
        if (explicitlyDisputed || confirmationStatus == ConfirmationStatus.DISPUTED) {
            return EvidenceStatus.DISPUTED
        }
        if (simulatedApi) {
            return EvidenceStatus.SOURCE_VERIFIED_SIMULATED
        }
        if (hasConfirmedBankMatch) {
            return EvidenceStatus.SOURCE_MATCHED
        }
        if (hasConfirmedDocument) {
            return EvidenceStatus.DOCUMENT_EXTRACTED
        }
        return EvidenceStatus.SELF_DECLARED
    }

    /**
     * Computes next evidence status when a deterministic bank credit reconciliation is confirmed.
     * Preserves DISPUTED or SOURCE_VERIFIED_SIMULATED if already higher priority.
     */
    fun onBankMatchConfirmed(current: EvidenceStatus): EvidenceStatus {
        return when (current) {
            EvidenceStatus.DISPUTED -> EvidenceStatus.DISPUTED
            EvidenceStatus.SOURCE_VERIFIED_SIMULATED -> EvidenceStatus.SOURCE_VERIFIED_SIMULATED
            EvidenceStatus.SELF_DECLARED,
            EvidenceStatus.DOCUMENT_EXTRACTED,
            EvidenceStatus.SOURCE_MATCHED -> EvidenceStatus.SOURCE_MATCHED
        }
    }

    /**
     * Computes next evidence status when a simulated party confirmation request is answered.
     * Rule 7: Confirmation updates relationship/payment status, NOT the evidence label —
     * only DISPUTED overrides the evidence label.
     */
    fun onSimulatedPartyResponse(response: ConfirmationStatus, fallback: EvidenceStatus): EvidenceStatus {
        return when (response) {
            ConfirmationStatus.DISPUTED -> EvidenceStatus.DISPUTED
            ConfirmationStatus.CONFIRMED_SIMULATED,
            ConfirmationStatus.RESPONDED,
            ConfirmationStatus.DECLINED,
            ConfirmationStatus.PENDING -> fallback
        }
    }

    /**
     * Computes next evidence status when a dispute is cleared by the worker based on underlying proof.
     */
    fun onDisputeCleared(
        hasConfirmedBankMatch: Boolean,
        hasConfirmedDocument: Boolean,
        simulatedApi: Boolean = false
    ): EvidenceStatus {
        return classifyFromSignals(
            hasConfirmedDocument = hasConfirmedDocument,
            hasConfirmedBankMatch = hasConfirmedBankMatch,
            confirmationStatus = null,
            explicitlyDisputed = false,
            simulatedApi = simulatedApi
        )
    }
}
