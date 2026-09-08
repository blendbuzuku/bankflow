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
  /** ISO 3166-1 alpha-2. Written into the beneficiary's PstlAdr. */
  creditorCountry?: string | null;
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
  /**
   * The same period with round trips taken off both sides.
   *
   * totalCredits and totalDebits are gross turnover, which is what camt.053
   * carries and what the entry list shows. This is what a customer means by
   * paid in and paid out.
   */
  netted: {
    paidIn: number;
    paidOut: number;
    paidInCount: number;
    paidOutCount: number;
    reversed: number;
  };
  entries: StatementEntry[];
}

/**
 * A payment the scheme still has something to say about.
 *
 * Described from the counterparty's side — whose account the money is going
 * to, and which bank holds it — because that is who is being played.
 */
export interface SchemeAction {
  transactionReference: string;
  endToEndId: string | null;
  amount: number;
  currency: string;
  rail: string;
  creditorName: string | null;
  creditorIban: string | null;
  creditorAgentBic: string | null;
  sentAt: string;
  recallId: string | null;
  recallReason: string | null;
}

export interface SchemeQueue {
  awaitingStatus: SchemeAction[];
  returnable: SchemeAction[];
}

/**
 * One line of the tariff.
 *
 * A charge is a fixed amount, a percentage of the payment, or both, optionally
 * floored and capped -- which is how banks actually price: flat on retail
 * clearing, a percentage on high value with a cap so a large payment does not
 * attract an absurd charge.
 */
export interface TariffRule {
  id: number;
  ruleCode: string;
  description: string;
  paymentType: string;
  paymentTypeName: string;
  currency: string;
  minAmount: number;
  maxAmount: number | null;
  fixedFee: number;
  percentageRate: number;
  minFee: number | null;
  maxFee: number | null;
  active: boolean;
  validFrom: string;
  validTo: string | null;
}

export interface TariffRuleRequest {
  ruleCode: string;
  description: string;
  paymentType: string;
  currency: string;
  minAmount: number;
  maxAmount?: number | null;
  fixedFee?: number;
  percentageRate?: number;
  minFee?: number | null;
  maxFee?: number | null;
  validFrom?: string | null;
  validTo?: string | null;
}

/** One scheme message in the traffic log, without its body. */
export interface MessageSummary {
  messageId: string;
  messageType: string;
  description: string;
  direction: 'OUTBOUND' | 'INBOUND';
  status: string;
  transactionReference: string | null;
  endToEndId: string | null;
  statusCode: string | null;
  reasonCode: string | null;
  sizeBytes: number;
  createdAt: string;
}

/** A definition present in the traffic, and what it means. */
export interface MessageTypeOption {
  name: string;
  identifier: string;
  description: string;
  count: number;
}

export interface MessageSearch {
  q?: string;
  type?: string;
  direction?: string;
  limit?: number;
}

// --- end of day ---

export interface DayActivityLine {
  label: string;
  count: number;
  total: number;
  fees: number;
}

export interface InFlightPayment {
  transactionReference: string;
  amount: number;
  currency: string;
  beneficiary: string | null;
  bookingDate: string;
  ageInDays: number;
}

export interface OutstandingItem {
  kind: string;
  count: number;
  description: string;
  where: string;
}

export interface DaySummary {
  bookingDate: string;
  closed: boolean;
  activity: DayActivityLine[];
  inFlight: InFlightPayment[];
  outstanding: OutstandingItem[];
}

export interface DayCloseRecord {
  bookingDate: string;
  closedByUsername: string;
  closedAt: string;
  balanced: boolean;
  reconciled: boolean;
  entryCount: number;
  totalDebits: number;
  totalCredits: number;
  matchedMovements: number;
  summary: string;
}

export interface BusinessDate {

  /** The day the bank is working in, which a close moves forward. */
  tradingInto: string;

  /** The date on the wall, which it does not. */
  calendarDate: string;

  daysBehind: number;
}

export interface DayCloseResult {
  bookingDate: string;
  closed: boolean;
  trialBalance: TrialBalance;
  reconciliation: Reconciliation;
  summary: string;
  record: DayCloseRecord | null;

  /** The day the bank moved on to, which a successful close advances. */
  tradingInto: string;
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
  entityType: string | null;
  entityId: string | null;
  actorUsername: string | null;
  actorRole: string | null;
  summary: string;
  details: string | null;
  /**
   * Whether this is money moving, or a decision about money.
   *
   * Derived from the event type on the server, never set by a caller, so a
   * settlement cannot be recorded as having moved nothing.
   */
  financial: boolean;
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

  /**
   * The trail for something that is not a payment.
   *
   * A client or an account carries no transaction reference, so the
   * per-payment call below cannot reach these. This is where "who approved
   * this client, and when" gets answered.
   */
  entityAuditTrail(
    entityType: string,
    entityId: string | number,
  ): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(
      `/api/transactions/audit/${entityType}/${entityId}`,
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

  /**
   * Signs the day off. A day that does not prove is not closed, and the
   * result says so rather than failing.
   */
  closeDay(date?: string): Observable<DayCloseResult> {
    return this.http.post<DayCloseResult>(
      `/api/end-of-day/close${date ? '?date=' + date : ''}`,
      {},
    );
  }

  // --- approvals ---------------------------------------------------------

  /**
   * Finds messages by whatever the person knows — an IBAN, a name, an amount,
   * a reference. A blank search returns the most recent traffic.
   */
  searchMessages(search: MessageSearch = {}): Observable<MessageSummary[]> {

    const params = new URLSearchParams();

    if (search.q?.trim()) { params.set('q', search.q.trim()); }
    if (search.type) { params.set('type', search.type); }
    if (search.direction) { params.set('direction', search.direction); }

    params.set('limit', String(search.limit ?? 100));

    return this.http.get<MessageSummary[]>(`/api/messages?${params}`);
  }

  /** The scheme messages behind one of the customer's own payments. */
  myMessages(reference: string): Observable<PacsMessage[]> {
    return this.http.get<PacsMessage[]>(
      `/api/my/transactions/${encodeURIComponent(reference)}/messages`,
    );
  }

  myMessageXml(messageId: string): Observable<string> {
    return this.http.get(
      `/api/my/messages/${encodeURIComponent(messageId)}/xml`,
      { responseType: 'text' },
    );
  }

  /** Which definitions are actually present, with what each one means. */
  messageTypes(): Observable<MessageTypeOption[]> {
    return this.http.get<MessageTypeOption[]>('/api/messages/types');
  }

  /** What the day consisted of, and what is still open. */
  daySummary(date: string): Observable<DaySummary> {
    return this.http.get<DaySummary>(`/api/end-of-day/summary?date=${date}`);
  }

  /** The recent run of days and whether each one closed. */
  dayHistory(): Observable<DayCloseRecord[]> {
    return this.http.get<DayCloseRecord[]>('/api/end-of-day/history');
  }

  /**
   * Which day the bank is trading into, and how far that is behind the clock.
   *
   * The browser's own date is no answer to this. Closing a day moves the bank
   * on without moving the calendar, so the two agree right up until the moment
   * somebody signs a day off — which is exactly when a screen asking the wrong
   * one starts showing the day that was just sealed.
   */
  businessDate(): Observable<BusinessDate> {
    return this.http.get<BusinessDate>('/api/end-of-day/business-date');
  }

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
  simulateReturn(
    reference: string,
    reasonCode: string,
    returnedAmount?: string,
  ): Observable<string> {
    const amount = returnedAmount
      ? `&returnedAmount=${encodeURIComponent(returnedAmount)}` : '';
    return this.http.post(
      `/api/kips/simulate/return/${encodeURIComponent(reference)}`
        + `?reason=${encodeURIComponent(reasonCode)}${amount}`,
      null,
      { responseType: 'text' },
    );
  }

  /** What the scheme still owes an answer on, from its side. */
  schemeQueue(): Observable<SchemeQueue> {
    return this.http.get<SchemeQueue>('/api/kips/queue');
  }

  /** Delivers a message to us as though the scheme had sent it. */
  deliverInbound(xml: string): Observable<string> {
    return this.http.post('/api/kips/inbound', xml, {
      headers: { 'Content-Type': 'application/xml' },
      responseType: 'text',
    });
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

  // --- tariff ------------------------------------------------------------

  /** Every line, current and retired: a retired one explains an old charge. */
  tariff(): Observable<TariffRule[]> {
    return this.http.get<TariffRule[]>('/api/tariff');
  }

  addTariffRule(request: TariffRuleRequest): Observable<TariffRule> {
    return this.http.post<TariffRule>('/api/tariff', request);
  }

  /** Supersedes a line from a date rather than rewriting what it charged. */
  amendTariffRule(
    id: number,
    request: TariffRuleRequest,
  ): Observable<TariffRule> {
    return this.http.put<TariffRule>(`/api/tariff/${id}`, request);
  }

  retireTariffRule(id: number): Observable<TariffRule> {
    return this.http.post<TariffRule>(`/api/tariff/${id}/retire`, null);
  }

  reinstateTariffRule(id: number): Observable<TariffRule> {
    return this.http.post<TariffRule>(`/api/tariff/${id}/reinstate`, null);
  }
}
