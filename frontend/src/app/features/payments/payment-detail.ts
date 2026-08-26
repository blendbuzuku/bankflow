import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import {
  AuditEvent,
  PacsMessage,
  TransactionResponse,
  TransactionService,
} from '../../core/services/transaction';

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
  imports: [DecimalPipe, DatePipe, FormsModule],
  templateUrl: './payment-detail.html',
  styleUrl: './payment-detail.css',
})
export class PaymentDetail implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly transactionService = inject(TransactionService);

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

    this.transactionService.messages(reference).subscribe({
      next: messages => this.messages.set(messages),
      error: () => { /* as above */ },
    });
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
