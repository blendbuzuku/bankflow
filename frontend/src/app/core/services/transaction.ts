import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Currency } from './account';

export type PaymentTypeCode =
  | 'INTERNAL'
  | 'KIPS_ACH'
  | 'KIPS_RTGS'
  | 'INTERNATIONAL';

export type ChargeBearer = 'DEBT' | 'CRED' | 'SHAR' | 'SLEV';

export type PurposeCode =
  | 'SALA' | 'PENS' | 'SUPP' | 'TAXS' | 'RENT'
  | 'LOAN' | 'INTC' | 'TRAD' | 'CASH' | 'OTHR';

export type TransactionStatus =
  | 'PENDING' | 'PENDING_APPROVAL' | 'DECLINED' | 'PROCESSING'
  | 'SENT' | 'COMPLETED' | 'SETTLED' | 'REJECTED' | 'RETURNED' | 'FAILED';

/**
 * The rails this bank can reach, with the constraints each scheme imposes.
 *
 * Mirrors the PaymentType enum on the server so the form can refuse an
 * impossible combination before asking the backend. The server remains the
 * authority; this only avoids a round trip to be told what we already know.
 */
export interface Rail {
  code: PaymentTypeCode;
  label: string;
  external: boolean;
  chargeBearers: ChargeBearer[];
  hint: string;
}

export const RAILS: Rail[] = [
  {
    code: 'INTERNAL',
    label: 'Internal transfer',
    external: false,
    chargeBearers: ['DEBT', 'CRED', 'SHAR', 'SLEV'],
    hint: 'Both parties bank with us. Settles immediately on our own books.',
  },
  {
    code: 'KIPS_ACH',
    label: 'KIPS retail clearing (ACH)',
    external: true,
    chargeBearers: ['SLEV'],
    hint: 'The scheme permits only SLEV charges on this rail.',
  },
  {
    code: 'KIPS_RTGS',
    label: 'KIPS real-time gross settlement',
    external: true,
    chargeBearers: ['DEBT', 'CRED', 'SHAR', 'SLEV'],
    hint: 'High-value, settles individually. Carries a UETR.',
  },
  {
    code: 'INTERNATIONAL',
    label: 'International (SWIFT)',
    external: true,
    chargeBearers: ['DEBT', 'CRED', 'SHAR'],
    hint: 'Correspondent banking for payments leaving Kosovo.',
  },
];

export interface FeeAssessment {
  totalFee: number;
  debtorFee: number;
  creditorFee: number;
  currency: Currency;
  ruleCode: string | null;
  description: string;
  free: boolean;
}

export interface TransferRequest {
  sourceAccountId: number;
  destinationAccountId?: number | null;
  amount: number;
  currency: Currency;
  endToEndId: string;
  instructionId: string;
  paymentType: PaymentTypeCode;
  chargeBearer?: ChargeBearer;
  purposeCode?: PurposeCode | null;
  remittanceInformation?: string | null;
  debtorName?: string | null;
  creditorName?: string | null;
  creditorIban?: string | null;
  creditorAgentBic?: string | null;
}

export interface TransactionResponse {
  id: number;
  transactionReference: string;
  endToEndId: string;
  instructionId: string;
  uetr: string | null;
  transactionType: string;
  paymentType: PaymentTypeCode | null;
  paymentTypeCode: string | null;
  paymentTypeName: string | null;
  direction: string | null;
  serviceLevel: string | null;
  chargeBearer: ChargeBearer | null;
  purposeCode: PurposeCode | null;
  status: TransactionStatus;
  sourceAccountId: number | null;
  destinationAccountId: number | null;
  amount: number;
  currency: Currency;
  debtorName: string | null;
  debtorIban: string | null;
  creditorName: string | null;
  creditorIban: string | null;
  creditorAgentBic: string | null;
  remittanceInformation: string | null;
  feeAmount: number | null;
  debtorFeeAmount: number | null;
  createdByUsername: string | null;
  approvedByUsername: string | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  reasonCode: string | null;
  bookingDate: string;
  valueDate: string;
  createdAt: string;
}

// --- recalls and returns ---

export type RecallDirection = 'OUTBOUND' | 'INBOUND';
export type RecallStatus = 'REQUESTED' | 'ACCEPTED' | 'REJECTED';

export interface RecallResponse {
  id: number;
  cancellationId: string;
  transactionReference: string;
  direction: RecallDirection;
  status: RecallStatus;
  reasonCode: string;
  reasonDescription: string;
  additionalInformation: string | null;
  requestedByUsername: string | null;
  decidedByUsername: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface StatementEntry {
  entryReference: string | null;
  transactionReference: string | null;
  creditDebitIndicator: 'CRDT' | 'DBIT';
  amount: number;
  currency: Currency;
  bookingDate: string;
  valueDate: string | null;
  balanceAfter: number;
  description: string;
}

export interface StatementResponse {
  statementId: string;
  accountId: number;
  iban: string;
  accountName: string | null;
  currency: Currency;
  from: string;
  to: string;
  openingBalance: number;
  closingBalance: number;
  totalCredits: number;
  totalDebits: number;
  creditCount: number;
  debitCount: number;
  entries: StatementEntry[];
}

export interface CustomerLimits {
  selfServiceEnabled: boolean;
  permittedRails: PaymentTypeCode[];
  limits: Record<Currency, number>;
}

/**
 * The two end-of-day proofs.
 *
 * The trial balance shows the ledger adds up; reconciliation shows it matches
 * what actually moved. A day is only closed when both pass — a ledger missing
 * both sides of a movement still nets to zero, so the first proof alone cannot
 * see a movement that never reached it.
 */
export interface CurrencyBalance {
  currency: Currency;
  totalDebits: number;
  totalCredits: number;
  net: number;
  entryCount: number;
  balanced: boolean;
}

export interface TrialBalance {
  bookingDate: string;
  currencies: CurrencyBalance[];
  balanced: boolean;
}

export interface ReconciliationBreak {
  operationId: string;
  accountId: number | null;
  direction: string | null;
  ledgerAmount: number | null;
  actualAmount: number | null;
  detail: string;
}

export interface Reconciliation {
  bookingDate: string;
  reconciled: boolean;
  breakCount: number;
  matched: number;
  unverifiable: number;
  unrecordedMoves: ReconciliationBreak[];
  unmatchedLedger: ReconciliationBreak[];
  amountMismatch: ReconciliationBreak[];
}

export interface DayClose {
  bookingDate: string;
  closed: boolean;
  trialBalance: TrialBalance;
  reconciliation: Reconciliation;
}

export interface AuditEvent {
  id: number;
  eventType: string;
  transactionReference: string | null;
  actorUsername: string | null;
  actorRole: string | null;
  summary: string;
  details: string | null;
  occurredAt: string;
}

export interface PacsMessage {
  id: number;
  messageId: string;
  messageType: string;
  direction: string;
  status: string;
  transactionReference: string | null;
  statusCode: string | null;
  reasonCode: string | null;
  createdAt: string;
}

@Service()
export class TransactionService {

  private readonly http = inject(HttpClient);

  /** What a payment would cost. Books nothing. */
  quote(
    paymentType: PaymentTypeCode,
    currency: Currency,
    amount: number,
    chargeBearer: ChargeBearer,
  ): Observable<FeeAssessment> {
    return this.http.post<FeeAssessment>('/api/transactions/quote', {
      paymentType,
      currency,
      amount,
      chargeBearer,
    });
  }

  /**
   * The exact XML that would go to KIPS, schema-checked, with nothing stored
   * and no money moved.
   */
  previewMessage(request: TransferRequest): Observable<string> {
    return this.http.post('/api/transactions/preview-message', request, {
      responseType: 'text',
    });
  }

  createTransfer(request: TransferRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(
      '/api/transactions/transfers',
      request,
    );
  }

  getByReference(reference: string): Observable<TransactionResponse> {
    return this.http.get<TransactionResponse>(
      `/api/transactions/reference/${reference}`,
    );
  }

  auditTrail(reference: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(
      `/api/transactions/reference/${reference}/audit`,
    );
  }

  messages(reference: string): Observable<PacsMessage[]> {
    return this.http.get<PacsMessage[]>(
      `/api/transactions/reference/${reference}/messages`,
    );
  }

  messageXml(messageId: string): Observable<string> {
    return this.http.get(`/api/transactions/messages/${messageId}/xml`, {
      responseType: 'text',
    });
  }

  // --- customer self-service ---------------------------------------------

  /**
   * What self-service allows, so the form can present the right options rather
   * than letting someone fill one in and then be refused.
   */
  myPaymentLimits(): Observable<CustomerLimits> {
    return this.http.get<CustomerLimits>('/api/my/payment-limits');
  }

  payAsCustomer(request: TransferRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/my/payments', request);
  }

  myTransactions(): Observable<TransactionResponse[]> {
    return this.http.get<TransactionResponse[]>('/api/my/transactions');
  }

  // --- cash over the counter ---------------------------------------------

  deposit(accountId: number, amount: number, narrative: string):
    Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/cash/deposits',
      { accountId, amount, narrative });
  }

  withdraw(accountId: number, amount: number, narrative: string):
    Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/cash/withdrawals',
      { accountId, amount, narrative });
  }

  /**
   * Stands in for the counterparty bank while there is no live KIPS link.
   *
   * Builds a real pacs.002 and feeds it through the same inbound path a genuine
   * one would take, so this exercises the production code rather than a
   * shortcut around it.
   */
  simulateCounterpartyResponse(
    reference: string,
    status: 'ACSC' | 'RJCT',
    reason?: string,
  ): Observable<string> {
    const query = `status=${status}` + (reason ? `&reason=${reason}` : '');
    return this.http.post(
      `/api/kips/simulate/status/${reference}?${query}`,
      {},
      { responseType: 'text' },
    );
  }

  // --- end of day --------------------------------------------------------

  trialBalance(date?: string): Observable<TrialBalance> {
    return this.http.get<TrialBalance>(
      `/api/end-of-day/trial-balance${date ? '?date=' + date : ''}`,
    );
  }

  reconciliation(date?: string): Observable<Reconciliation> {
    return this.http.get<Reconciliation>(
      `/api/end-of-day/reconciliation${date ? '?date=' + date : ''}`,
    );
  }

  closeDay(date?: string): Observable<DayClose> {
    return this.http.post<DayClose>(
      `/api/end-of-day/close${date ? '?date=' + date : ''}`,
      {},
    );
  }

  // --- approvals ---------------------------------------------------------

  // --- statements ---

  statement(accountId: number, from: string, to: string):
    Observable<StatementResponse> {

    return this.http.get<StatementResponse>(
      `/api/statements/account/${accountId}?from=${from}&to=${to}`,
    );
  }

  /** The same statement as camt.053, for anyone who wants the ISO form. */
  statementXml(accountId: number, from: string, to: string): Observable<string> {
    return this.http.get(
      `/api/statements/account/${accountId}/camt053?from=${from}&to=${to}`,
      { responseType: 'text' },
    );
  }

  // --- recalls ---

  /** Everything waiting for someone to decide, in either direction. */
  openRecalls(): Observable<RecallResponse[]> {
    return this.http.get<RecallResponse[]>('/api/recalls');
  }

  recallsFor(reference: string): Observable<RecallResponse[]> {
    return this.http.get<RecallResponse[]>(
      `/api/recalls/transaction/${encodeURIComponent(reference)}`,
    );
  }

  /** Asks the beneficiary's bank to send one of our payments back. */
  requestRecall(
    transactionReference: string,
    reasonCode: string,
    note: string,
  ): Observable<RecallResponse> {

    return this.http.post<RecallResponse>('/api/recalls', {
      transactionReference, reasonCode, note,
    });
  }

  acceptRecall(id: number, note: string): Observable<RecallResponse> {
    return this.http.post<RecallResponse>(`/api/recalls/${id}/accept`, { note });
  }

  rejectRecall(id: number, note: string): Observable<RecallResponse> {
    return this.http.post<RecallResponse>(`/api/recalls/${id}/reject`, { note });
  }

  /** Simulates the beneficiary bank sending a settled payment back. */
  simulateReturn(reference: string, reasonCode: string): Observable<string> {
    return this.http.post(
      `/api/kips/simulate/return/${encodeURIComponent(reference)}`
        + `?reason=${encodeURIComponent(reasonCode)}`,
      null,
      { responseType: 'text' },
    );
  }

  awaitingApproval(): Observable<TransactionResponse[]> {
    return this.http.get<TransactionResponse[]>('/api/approvals');
  }

  approve(reference: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(
      `/api/approvals/${reference}/approve`,
      {},
    );
  }

  decline(reference: string, reason: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(
      `/api/approvals/${reference}/decline?reason=${encodeURIComponent(reason)}`,
      {},
    );
  }
}
