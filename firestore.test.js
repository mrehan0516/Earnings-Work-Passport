const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ASHA_UID = "asha_owner_uid";
const OTHER_UID = "other_worker_uid";
const WORKER_ID = "worker_asha_demo";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

async function seedWorker(db, uid, workerId = WORKER_ID) {
  const ts = pastDate(10000);
  await db.collection("workers").doc(workerId).set({
    userId: uid,
    displayName: "Asha Rao",
    city: "Bengaluru",
    primaryWorkType: "Delivery & Freelance Tutoring",
    demoProfile: true,
    createdAt: ts,
    updatedAt: ts,
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any of the 10 collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const collections = [
    "workers",
    "parties",
    "relationships",
    "income_records",
    "documents",
    "bank_credits",
    "reconciliations",
    "confirmation_requests",
    "share_links",
    "audit_log",
  ];
  for (const col of collections) {
    await assertFails(unauthDb.collection(col).get());
  }
  await assertFails(
    unauthDb.collection("workers").doc("unauth_worker").set({
      userId: ASHA_UID,
      displayName: "Hacker",
      city: "Delhi",
      primaryWorkType: "None",
      demoProfile: false,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Owner can create, update, and query all 10 collections with Kotlin-mirrored payloads", async () => {
  const ashaDb = testEnv.authenticatedContext(ASHA_UID).firestore();
  const ts = pastDate(10000);

  // 1. workers
  await assertSucceeds(seedWorker(ashaDb, ASHA_UID));
  await assertSucceeds(
    ashaDb.collection("workers").doc(WORKER_ID).update({
      city: "Bengaluru, KA",
      updatedAt: pastDate(1000),
    })
  );

  // 2. parties (new types, roles, non-verified partyContactStatus, optional reg only for non-individuals)
  await assertSucceeds(
    ashaDb.collection("parties").doc("party_swiggy").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Swiggy Delivery",
      partyType: "PLATFORM",
      contactHint: "Partner ID: SWG-4821",
      roles: ["WORK_GIVER", "PAYER"],
      phone: "",
      email: "support@swiggy-demo.invalid",
      note: "Weekly payouts",
      registrationNumber: "29DEMO0000A1Z5",
      partyContactStatus: "added_by_worker",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("parties").doc("party_razorpay").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Razorpay Payouts",
      partyType: "PAYMENT_PROVIDER",
      contactHint: "",
      roles: ["PAYER"],
      phone: "",
      email: "",
      note: "Settlement rail",
      registrationNumber: "",
      partyContactStatus: "added_by_worker",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("parties").doc("party_individual").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Kulkarni Household",
      partyType: "INDIVIDUAL",
      contactHint: "",
      roles: ["WORK_GIVER", "PAYER"],
      phone: "+91 98000 00102",
      email: "",
      note: "Individual household — no registration needed",
      registrationNumber: "",
      partyContactStatus: "added_by_worker",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Individuals cannot carry a registration number
  await assertFails(
    ashaDb.collection("parties").doc("party_individual_bad_reg").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Individual With GST",
      partyType: "INDIVIDUAL",
      contactHint: "",
      registrationNumber: "29INVALID1234",
      partyContactStatus: "added_by_worker",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Parties can never be labeled "verified"
  await assertFails(
    ashaDb.collection("parties").doc("party_verified_rejected").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Fake Verified",
      partyType: "BUSINESS",
      contactHint: "",
      partyContactStatus: "verified",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 3. relationships (with relationshipStatus=worker_declared)
  await assertSucceeds(
    ashaDb.collection("relationships").doc("rel_swiggy").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "party_swiggy",
      roleTitle: "Delivery Partner",
      payoutCycle: "WEEKLY",
      status: "ACTIVE",
      relationshipStatus: "worker_declared",
      roles: ["WORK_GIVER", "PAYER"],
      startedOn: "2025-04-01",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 4. documents (with fileHash + immutability check + CSV_IMPORT / CASH_NOTE enums)
  await assertSucceeds(
    ashaDb.collection("documents").doc("doc_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "party_swiggy",
      title: "Swiggy Weekly Payout Oct W2",
      documentType: "PLATFORM_STATEMENT",
      sourceKind: "FILE_UPLOAD",
      reviewStatus: "PENDING_REVIEW",
      fileHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      extractedPartyName: "Swiggy Delivery",
      extractedAmountRupees: 14250,
      extractedDate: "2026-10-14",
      extractedReference: "SWG261014889",
      extractionNotes: "AI extracted net payout amount; worker confirmation required.",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    ashaDb.collection("documents").doc("doc_csv_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "",
      title: "SBI_Nov_Statement.csv",
      documentType: "BANK_STATEMENT_CSV",
      sourceKind: "CSV_IMPORT",
      reviewStatus: "CONFIRMED",
      fileHash: "a1b2c3d4e5f60718293a4b5c6d7e8f9011223344556677889900aabbccddeeff",
      extractedPartyName: "SBI Statement CSV",
      extractedAmountRupees: 28000,
      extractedDate: "2026-11-16",
      extractedReference: "3 rows",
      extractionNotes: "Parsed 3 bank credit rows in code (no AI).",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    ashaDb.collection("documents").doc("doc_1").update({
      reviewStatus: "CONFIRMED",
      updatedAt: pastDate(1000),
    })
  );

  // fileHash immutability check: cannot tamper with fileHash after creation
  await assertFails(
    ashaDb.collection("documents").doc("doc_1").update({
      fileHash: "0000000000000000000000000000000000000000000000000000000000000000",
      updatedAt: pastDate(500),
    })
  );

  // 5. income_records (with detailed breakdown + immutable aiOriginal snapshot)
  await assertSucceeds(
    ashaDb.collection("income_records").doc("inc_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "party_swiggy",
      partyName: "Swiggy Delivery",
      workGiverPartyId: "party_swiggy",
      workGiverPartyName: "Swiggy Delivery",
      payerPartyId: "party_razorpay",
      payerPartyName: "Razorpay Payouts",
      documentId: "doc_1",
      amountRupees: 14250,
      earnedDate: "2026-10-14",
      periodLabel: "2026-10-08 to 2026-10-14",
      referenceCode: "SWG261014889",
      invoiceNumber: "",
      periodStart: "2026-10-08",
      periodEnd: "2026-10-14",
      grossAmountRupees: 14500,
      deductionsRupees: 250,
      netAmountRupees: 14250,
      invoiceAmountRupees: 0,
      aiOriginal: {
        document_type: "platform_statement",
        platform: "Swiggy Delivery",
        record_date: "2026-10-14",
        gross_amount: "14500",
        deductions: "250",
        net_amount: "14250",
        reference: "SWG261014889",
      },
      evidenceStatus: "DOCUMENT_EXTRACTED",
      workerConfirmed: true,
      simulatedApi: true,
      notes: "Confirmed by worker after AI extraction",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // aiOriginal immutability check: tampering with aiOriginal after confirmation must fail
  await assertFails(
    ashaDb.collection("income_records").doc("inc_1").update({
      aiOriginal: { platform: "Tampered" },
      updatedAt: pastDate(500),
    })
  );

  // 6. bank_credits (with classificationStatus & evidenceStatus)
  await assertSucceeds(
    ashaDb.collection("bank_credits").doc("bc_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      bankLabel: "Demo Bank ••4821",
      creditDate: "2026-10-15",
      amountRupees: 14250,
      narration: "IMPS/RIDECO PAYOUT/RC46001",
      utrReference: "UTR628810150041",
      matchedIncomeRecordId: "",
      classificationStatus: "UNCLASSIFIED",
      evidenceStatus: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 7. reconciliations + partial updates to income_records & bank_credits
  await assertSucceeds(
    ashaDb.collection("reconciliations").doc("rec_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      incomeRecordId: "inc_1",
      bankCreditId: "bc_1",
      matchRule: "Exact amount & reference overlap within 1 day",
      amountDeltaRupees: 0,
      dateGapDays: 1,
      decisionStatus: "CONFIRMED",
      matchOutcome: "STRONG",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("income_records").doc("inc_1").update({
      evidenceStatus: "SOURCE_MATCHED",
      duplicateGroupKey: "rideco|2026100820261014|14250|swg261014889",
      groupedIntoRecordId: "",
      duplicateIgnored: true,
      matchOutcome: "STRONG",
      paymentStatus: "PAYMENT_SENT",
      paymentDate: "2026-10-15",
      conflictReason: "",
      manuallyDisputed: false,
      notes: "Matched by rules to Demo Bank credit",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    ashaDb.collection("income_records").doc("inc_1").update({
      duplicateIgnored: "yes",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    ashaDb.collection("bank_credits").doc("bc_1").update({
      matchedIncomeRecordId: "inc_1",
      classificationStatus: "MATCHED",
      evidenceStatus: "SOURCE_MATCHED",
      updatedAt: pastDate(1000),
    })
  );

  // 8. confirmation_requests (PENDING -> CONFIRMED_SIMULATED, then terminal locked)
  await assertSucceeds(
    ashaDb.collection("confirmation_requests").doc("conf_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      incomeRecordId: "inc_1",
      partyId: "party_swiggy",
      partyName: "Swiggy Delivery",
      requestedAmountRupees: 14250,
      status: "PENDING",
      responseNote: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("confirmation_requests").doc("conf_1").update({
      status: "CONFIRMED_SIMULATED",
      responseNote: "Client confirmed work delivered, Rs 14,250, payment sent on 2026-11-19.",
      workDelivered: true,
      confirmedAmountRupees: 14250,
      paymentSent: true,
      paymentDate: "2026-11-19",
      updatedAt: pastDate(1000),
    })
  );
  // Terminal state locking check: cannot modify after CONFIRMED_SIMULATED
  await assertFails(
    ashaDb.collection("confirmation_requests").doc("conf_1").update({
      status: "DISPUTED",
      responseNote: "Tampered",
      updatedAt: pastDate(500),
    })
  );

  // 9. share_links (ACTIVE -> REVOKED, then terminal locked)
  await assertSucceeds(
    ashaDb.collection("share_links").doc("share_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      workerName: "Asha Rao",
      shareCode: "ASHA-8492",
      recipientLabel: "Indiranagar House Owner",
      dateRangeLabel: "Oct 2026 - Nov 2026",
      includedPartyIds: ["party_swiggy"],
      includedEvidenceLevels: ["SOURCE_MATCHED", "SOURCE_VERIFIED_SIMULATED"],
      maskBankRefs: true,
      expiresOn: "2026-12-20",
      status: "ACTIVE",
      totalIncludedRupees: 14250,
      summaryLines: ["Swiggy Delivery | Rs 14,250 | Source-matched"],
      consentText: "Worker consented to read-only sharing. Not a credit score or loan approval.",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("share_links").doc("share_1").update({
      status: "REVOKED",
      updatedAt: pastDate(1000),
    })
  );
  // Terminal state locking check: REVOKED cannot be re-activated
  await assertFails(
    ashaDb.collection("share_links").doc("share_1").update({
      status: "ACTIVE",
      updatedAt: pastDate(500),
    })
  );

  // 10. audit_log (append-only: create succeeds, update fails)
  await assertSucceeds(
    ashaDb.collection("audit_log").doc("log_1").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      actionType: "RECONCILIATION_CONFIRMED",
      actorRole: "RULES",
      targetCollection: "income_records",
      targetId: "inc_1",
      summary: "Matched Rs 14,250 payout with SBI credit UTR628810150041.",
      createdAt: ts,
    })
  );
  await assertFails(
    ashaDb.collection("audit_log").doc("log_1").update({
      summary: "Rewritten history",
    })
  );

  // Verify Kotlin repository queries succeed with userId filter and fail without filter
  const collections = [
    "workers",
    "parties",
    "relationships",
    "income_records",
    "documents",
    "bank_credits",
    "reconciliations",
    "confirmation_requests",
    "share_links",
    "audit_log",
  ];
  for (const col of collections) {
    await assertSucceeds(ashaDb.collection(col).where("userId", "==", ASHA_UID).get());
    await assertFails(ashaDb.collection(col).get());
  }
});

test("Cross-user isolation: another user cannot read, list, update, or delete Asha's data", async () => {
  const ashaDb = testEnv.authenticatedContext(ASHA_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  await seedWorker(ashaDb, ASHA_UID);

  const ts = pastDate(8000);
  await ashaDb.collection("income_records").doc("inc_private").set({
    userId: ASHA_UID,
    workerId: WORKER_ID,
    partyId: "",
    partyName: "Private Client",
    documentId: "",
    amountRupees: 8500,
    earnedDate: "2026-10-10",
    periodLabel: "Oct 2026",
    referenceCode: "",
    evidenceStatus: "SELF_DECLARED",
    workerConfirmed: true,
    notes: "Private",
    createdAt: ts,
    updatedAt: ts,
  });

  await assertFails(otherDb.collection("workers").doc(WORKER_ID).get());
  await assertFails(otherDb.collection("income_records").doc("inc_private").get());
  await assertFails(
    otherDb.collection("income_records").where("userId", "==", ASHA_UID).get()
  );
  await assertFails(
    otherDb.collection("income_records").doc("inc_private").update({
      notes: "Hijacked",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(otherDb.collection("income_records").doc("inc_private").delete());
});

test("Hardened validation rejects ghost fields, @DocumentId 'id', future timestamps, and orphan records", async () => {
  const ashaDb = testEnv.authenticatedContext(ASHA_UID).firestore();
  await seedWorker(ashaDb, ASHA_UID);
  const ts = pastDate(5000);

  // Ghost field / @DocumentId 'id' rejection
  await assertFails(
    ashaDb.collection("parties").doc("party_bad_id").set({
      id: "party_bad_id",
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Bad Party",
      partyType: "CLIENT",
      contactHint: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Future timestamp rejection
  await assertFails(
    ashaDb.collection("parties").doc("party_future").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Future Party",
      partyType: "CLIENT",
      contactHint: "",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Invalid evidence status enum rejection
  await assertFails(
    ashaDb.collection("income_records").doc("inc_bad_status").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "",
      partyName: "Test",
      documentId: "",
      amountRupees: 1000,
      earnedDate: "2026-10-12",
      periodLabel: "Oct",
      referenceCode: "",
      evidenceStatus: "AI_VERIFIED",
      workerConfirmed: true,
      notes: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Orphan record referencing non-existent workerId rejected
  await assertFails(
    ashaDb.collection("parties").doc("party_orphan").set({
      userId: ASHA_UID,
      workerId: "non_existent_worker",
      name: "Orphan",
      partyType: "CLIENT",
      contactHint: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Atomic batch seeding of worker + child collections succeeds via existsAfter", async () => {
  const ashaDb = testEnv.authenticatedContext(ASHA_UID).firestore();
  const ts = pastDate(5000);
  const batch = ashaDb.batch();

  batch.set(ashaDb.collection("workers").doc("batch_worker"), {
    userId: ASHA_UID,
    displayName: "Asha Rao",
    city: "Bengaluru",
    primaryWorkType: "Multi-platform gig worker",
    demoProfile: true,
    createdAt: ts,
    updatedAt: ts,
  });

  batch.set(ashaDb.collection("parties").doc("batch_party"), {
    userId: ASHA_UID,
    workerId: "batch_worker",
    name: "Urban Home Tutoring",
    partyType: "HOUSEHOLD",
    contactHint: "Parent group",
    createdAt: ts,
    updatedAt: ts,
  });

  await assertSucceeds(batch.commit());
});

test("Public confirmation_requests/{token} rules: pending unexpired get & answer-only update succeed; listing, expired, completed, and other fields fail", async () => {
  const ashaDb = testEnv.authenticatedContext(ASHA_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  await seedWorker(ashaDb, ASHA_UID);

  const ts = pastDate(8000);
  const validToken = "tok_9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a"; // 44 chars >= 32
  const expiredToken = "exp_9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a";
  const shortToken = "short_token_123";
  const futureExpiry = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
  const pastExpiry = new Date(Date.now() - 60 * 1000);

  // Create valid pending request (7-day expiry, >=32-char token)
  await assertSucceeds(
    ashaDb.collection("confirmation_requests").doc(validToken).set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      incomeRecordId: "inc_tutor_nov",
      partyId: "party_ravi",
      partyName: "Ravi Kumar",
      workerName: "Asha Rao",
      workDescription: "Home tutoring sessions (Nov W1-W2)",
      earnedDate: "2026-11-12",
      periodLabel: "Nov 2026",
      requestedAmountRupees: 5000,
      claimVersion: 1,
      claimSnapshot: {
        workerName: "Asha Rao",
        workDescription: "Home tutoring sessions (Nov W1-W2)",
        requestedAmountRupees: 5000,
        earnedDate: "2026-11-12",
        periodLabel: "Nov 2026",
        claimVersion: 1,
      },
      demoOtpCode: "482910",
      expiresAt: futureExpiry,
      status: "PENDING",
      responseNote: "",
      responses: {},
      otp_attempts: 0,
      workerResolution: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Create expired pending request
  await assertSucceeds(
    ashaDb.collection("confirmation_requests").doc(expiredToken).set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      incomeRecordId: "inc_tutor_old",
      partyId: "party_ravi",
      partyName: "Ravi Kumar",
      workerName: "Asha Rao",
      workDescription: "Old tutoring",
      earnedDate: "2026-10-01",
      periodLabel: "Oct 2026",
      requestedAmountRupees: 3000,
      claimVersion: 1,
      demoOtpCode: "111222",
      expiresAt: pastExpiry,
      status: "PENDING",
      responseNote: "",
      otp_attempts: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Create short-id request (should NOT be publicly readable)
  await assertSucceeds(
    ashaDb.collection("confirmation_requests").doc(shortToken).set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      incomeRecordId: "inc_short",
      partyId: "party_ravi",
      partyName: "Ravi Kumar",
      requestedAmountRupees: 2000,
      expiresAt: futureExpiry,
      status: "PENDING",
      responseNote: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 1. Public list is strictly denied
  await assertFails(unauthDb.collection("confirmation_requests").get());
  await assertFails(otherDb.collection("confirmation_requests").where("userId", "==", ASHA_UID).get());

  // 2. Public get succeeds ONLY for pending, unexpired >=32-char token
  await assertSucceeds(unauthDb.collection("confirmation_requests").doc(validToken).get());
  await assertFails(unauthDb.collection("confirmation_requests").doc(expiredToken).get());
  await assertFails(unauthDb.collection("confirmation_requests").doc(shortToken).get());

  // 3. Public update fails if trying to touch any field outside [responses, status, responded_at, otp_attempts]
  await assertFails(
    unauthDb.collection("confirmation_requests").doc(validToken).update({
      requestedAmountRupees: 99999,
      otp_attempts: 1,
    })
  );
  await assertFails(
    unauthDb.collection("confirmation_requests").doc(validToken).update({
      workerName: "Tampered Name",
    })
  );
  await assertFails(
    unauthDb.collection("confirmation_requests").doc(validToken).update({
      expiresAt: new Date(Date.now() + 999999999),
    })
  );
  await assertFails(
    unauthDb.collection("confirmation_requests").doc(expiredToken).update({
      otp_attempts: 1,
    })
  );

  // 4. Public update succeeds for OTP attempts while PENDING
  await assertSucceeds(
    unauthDb.collection("confirmation_requests").doc(validToken).update({
      otp_attempts: 1,
      responses: {
        otpLockedUntilEpochMs: 0,
      },
    })
  );

  // 5. Public update succeeds for submitting answer fields (responses, status, responded_at, otp_attempts)
  await assertSucceeds(
    unauthDb.collection("confirmation_requests").doc(validToken).update({
      status: "RESPONDED",
      responded_at: pastDate(1000),
      otp_attempts: 1,
      responses: {
        workDelivered: "yes",
        amountCorrect: "corrected_amount",
        correctedAmountRupees: 4500,
        paidStatus: "yes",
        paidDate: "2026-11-15",
        claimVersionShown: 1,
        otpVerifiedAtEpochMs: Date.now() - 2000,
      },
    })
  );

  // 6. After status is no longer PENDING, public get AND public update are both denied
  await assertFails(unauthDb.collection("confirmation_requests").doc(validToken).get());
  await assertFails(
    unauthDb.collection("confirmation_requests").doc(validToken).update({
      responses: { workDelivered: "no" },
    })
  );

  // 7. Owner can still read and record workerResolution / appliedToLedgerAt + new statuses
  await assertSucceeds(ashaDb.collection("confirmation_requests").doc(validToken).get());
  await assertSucceeds(
    ashaDb.collection("confirmation_requests").doc(validToken).update({
      workerResolution: "CORRECTION_ACCEPTED",
      appliedToLedgerAt: pastDate(500),
      responseNote: "Worker accepted client correction to Rs 4,500.",
      updatedAt: pastDate(500),
    })
  );

  // 8. Owner can write new statuses: contact_verified, counterparty_acknowledged, payer_confirmed
  await assertSucceeds(
    ashaDb.collection("parties").doc("party_ravi").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      name: "Ravi Kumar",
      partyType: "INDIVIDUAL",
      contactHint: "+91 98000 00111",
      partyContactStatus: "contact_verified",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("relationships").doc("rel_ravi").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "party_ravi",
      roleTitle: "Tutoring Client",
      payoutCycle: "MONTHLY",
      status: "ACTIVE",
      relationshipStatus: "counterparty_acknowledged",
      startedOn: "2026-08-01",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    ashaDb.collection("income_records").doc("inc_tutor_nov").set({
      userId: ASHA_UID,
      workerId: WORKER_ID,
      partyId: "party_ravi",
      partyName: "Ravi Kumar",
      documentId: "",
      amountRupees: 4500,
      earnedDate: "2026-11-12",
      periodLabel: "Nov 2026",
      referenceCode: "",
      evidenceStatus: "SELF_DECLARED",
      workerConfirmed: true,
      paymentStatus: "payer_confirmed",
      paymentDate: "2026-11-15",
      notes: "Confirmed by counterparty",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

