import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import {
  AuditEvent,
  PacsMessage,
  RecallResponse,
  TransactionResponse,
  TransactionService,
} from '../../core/services/transaction';
import { Toasts } from '../../core/services/toasts';
import { MessageView } from '../../shared/message-view';

/**
 * Everything known about one payment.
 *
 * Three views of the same event, deliberately together: what the payment is,
 * what happened to it, and what was actually exchanged with the scheme. Being
 * able to read the message next to the trail that produced it is what makes an
 * ISO 20022 flow inspectable rather than opaque.
 */
@Component({
  selector: 'app-payment-detail',
  imports: [DecimalPipe, DatePipe, FormsModule, MessageView],
  templateUrl: './payment-detail.html',
  styleUrl: './payment-detail.css',
})
export class PaymentDetail implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly transactionService = inject(TransactionService);
  private readonly toasts = inject(Toasts);

  readonly transaction = signal<TransactionResponse | null>(null);
  readonly audit = signal<AuditEvent[]>([]);
  readonly messages = signal<PacsMessage[]>([]);

  readonly loading = signal(true);
  readonly error = signal('');

  readonly openMessageId = signal<string | null>(null);
  readonly messageXml = signal('');
  readonly xmlError = signal('');

  /**
   * Reason codes a counterparty realistically sends back, taken from the KIPS
   * specification rather than the full ISO list.
   */
  readonly rejectionReasons = [
    { code: 'AC01', label: 'AC01 — Incorrect account number' },
    { code: 'AC04', label: 'AC04 — Account closed' },
    { code: 'AC06', label: 'AC06 — Account blocked' },
    { code: 'AM04', label: 'AM04 — Insufficient funds' },
    { code: 'AM05', label: 'AM05 — Duplicate' },
    { code: 'RR03', label: 'RR03 — Missing creditor name or address' },
  ];

  selectedReason = 'AC01';

  // --- recall ---

  readonly recalls = signal<RecallResponse[]>([]);
  readonly showRecallForm = signal(false);
  readonly recalling = signal(false);

  recallReason = 'CUST';
  recallNote = '';

  /** Why a settled payment might be asked back. */
  readonly recallReasons = [
    { code: 'CUST', label: 'The customer asked for it back' },
    { code: 'AC01', label: 'Wrong account number' },
    { code: 'AM05', label: 'Sent twice' },
    { code: 'RR04', label: 'Regulatory reason' },
  ];

  readonly simulating = signal(false);
  readonly simulateError = signal('');

  ngOnInit(): void {

    const reference = this.route.snapshot.paramMap.get('reference');

    if (!reference) {
      this.error.set('No payment reference given.');
      this.loading.set(false);
      return;
    }

    this.transactionService.getByReference(reference).subscribe({
      next: transaction => {
        this.transaction.set(transaction);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('That payment could not be found.');
        this.loading.set(false);
      },
    });

    /*
     * The trail and the messages are supporting detail. Failing to load them
     * should not hide the payment itself.
     */
    this.transactionService.auditTrail(reference).subscribe({
      next: events => this.audit.set(events),
      error: () => this.audit.set([]),
    });

    this.transactionService.messages(reference).subscribe({
      next: messages => this.messages.set(messages),
      error: () => this.messages.set([]),
    });

    this.loadRecalls(reference);
  }

  private loadRecalls(reference: string): void {

    this.transactionService.recallsFor(reference).subscribe({
      next: recalls => this.recalls.set(recalls),
      error: () => this.recalls.set([]),
    });
  }

  /**
   * A settled outbound payment is the only thing worth asking back: one still
   * in flight should be left to settle or fail on its own, and an internal
   * transfer never left the bank.
   */
  canRecall(): boolean {

    const t = this.transaction();

    return t?.status === 'SETTLED'
      && t?.direction === 'OUTBOUND'
      && !this.openRecall();
  }

  openRecall(): RecallResponse | undefined {
    return this.recalls().find(r => r.status === 'REQUESTED');
  }

  toggleRecallForm(): void {
    this.showRecallForm.set(!this.showRecallForm());
    this.recallNote = '';
  }

  requestRecall(): void {

    const reference = this.transaction()?.transactionReference;

    if (!reference) {
      return;
    }

    this.recalling.set(true);

    this.transactionService
      .requestRecall(reference, this.recallReason, this.recallNote)
      .subscribe({
        next: recall => {
          this.recalling.set(false);
          this.showRecallForm.set(false);
          this.recallNote = '';
          this.toasts.success(
            'Recall requested',
            `camt.056 ${recall.cancellationId} sent. No funds have moved yet.`,
          );
          this.loadRecalls(reference);
          this.refreshMessages(reference);
        },
        error: error => {
          this.recalling.set(false);
          this.toasts.failure('The recall could not be requested', error);
        },
      });
  }

  /** Plays the beneficiary bank agreeing and sending the money back. */
  simulateReturn(): void {

    const reference = this.transaction()?.transactionReference;

    if (!reference) {
      return;
    }

    this.recalling.set(true);

    this.transactionService.simulateReturn(reference, 'AC04').subscribe({
      next: () => {
        this.recalling.set(false);
        this.toasts.success(
          'Payment returned',
          'The funds are back with the debtor.',
        );
        this.reload(reference);
      },
      error: error => {
        this.recalling.set(false);
        this.toasts.failure('The return could not be simulated', error);
      },
    });
  }

  private refreshMessages(reference: string): void {

    this.transactionService.messages(reference).subscribe({
      next: messages => this.messages.set(messages),
      error: () => { /* the action succeeded; a stale list is not worth an error */ },
    });
  }



  /** Only a payment still in flight can receive a status report. */
  awaitingCounterparty(): boolean {
    return this.transaction()?.status === 'SENT';
  }

  simulate(status: 'ACSC' | 'RJCT'): void {

    const reference = this.transaction()?.transactionReference;

    if (!reference) {
      return;
    }

    this.simulating.set(true);
    this.simulateError.set('');

    this.transactionService.simulateCounterpartyResponse(
      reference,
      status,
      status === 'RJCT' ? this.selectedReason : undefined,
    ).subscribe({
      next: () => {
        this.simulating.set(false);
        this.reload(reference);
      },
      error: error => {
        this.simulating.set(false);
        this.simulateError.set(
          error?.error?.message ?? 'The response could not be processed.',
        );
      },
    });
  }

  private reload(reference: string): void {

    this.transactionService.getByReference(reference).subscribe({
      next: transaction => this.transaction.set(transaction),
      error: () => { /* keep what is on screen rather than blanking it */ },
    });

    this.transactionService.auditTrail(reference).subscribe({
      next: events => this.audit.set(events),
      error: () => { /* as above */ },
    });

    this.refreshMessages(reference);
    this.loadRecalls(reference);
  }

  toggleMessage(message: PacsMessage): void {

    if (this.openMessageId() === message.messageId) {
      this.openMessageId.set(null);
      this.messageXml.set('');
      return;
    }

    this.openMessageId.set(message.messageId);
    this.messageXml.set('');
    this.xmlError.set('');

    this.transactionService.messageXml(message.messageId).subscribe({
      next: xml => this.messageXml.set(xml),
      error: () => this.xmlError.set('The message could not be loaded.'),
    });
  }

  /**
   * Terminal states are styled differently from those still in flight, so the
   * standing of a payment reads at a glance.
   */
  statusTone(status: string | undefined): string {

    switch (status) {
      case 'COMPLETED':
      case 'SETTLED':
        return 'good';
      case 'REJECTED':
      case 'FAILED':
      case 'DECLINED':
      case 'RETURNED':
        return 'bad';
      case 'PENDING_APPROVAL':
        return 'warn';
      default:
        return 'info';
    }
  }

  eventTone(eventType: string): string {

    if (eventType.includes('REJECTED')
      || eventType.includes('FAILED')
      || eventType.includes('DECLINED')) {
      return 'bad';
    }

    if (eventType.includes('SETTLED') || eventType.includes('APPROVED')) {
      return 'good';
    }

    if (eventType.includes('APPROVAL')) {
      return 'warn';
    }

    return 'info';
  }

  /** Amount actually taken from the debtor: the payment plus their share of the charge. */
  totalDebited(): number | null {

    const t = this.transaction();

    if (!t) {
      return null;
    }

    return t.amount + (t.debtorFeeAmount ?? 0);
  }
}
