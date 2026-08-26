import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Toasts } from '../../core/services/toasts';

import {
  AccountResponse,
  AccountService,
  Currency,
} from '../../core/services/account';

import {
  ChargeBearer,
  PaymentTypeCode,
  PurposeCode,
  RAILS,
  Rail,
  TransactionService,
  TransferRequest,
  FeeAssessment,
} from '../../core/services/transaction';

/**
 * Where a bank worker enters a payment.
 *
 * The form is built around two things a teller needs before committing: what
 * it will cost, and exactly what will be sent to the scheme. Both are fetched
 * from the server rather than guessed at, so what is shown is what will happen.
 */
@Component({
  selector: 'app-payment-form',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './payment-form.html',
  styleUrl: './payment-form.css',
})
export class PaymentForm implements OnInit {

  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);

  readonly rails = RAILS;
  readonly purposeCodes: PurposeCode[] =
    ['SALA', 'PENS', 'SUPP', 'TAXS', 'RENT', 'LOAN', 'INTC', 'TRAD', 'CASH', 'OTHR'];

  readonly accounts = signal<AccountResponse[]>([]);
  readonly loadingAccounts = signal(true);

  // --- the instruction ---

  paymentType: PaymentTypeCode = 'KIPS_ACH';
  sourceAccountId: number | null = null;
  destinationAccountId: number | null = null;
  amount: number | null = null;
  currency: Currency = 'EUR';
  chargeBearer: ChargeBearer = 'SLEV';
  purposeCode: PurposeCode | '' = '';
  remittanceInformation = '';

  debtorName = '';
  creditorName = '';
  creditorIban = '';
  creditorAgentBic = '';

  // --- what the server says about it ---

  readonly quote = signal<FeeAssessment | null>(null);
  readonly quoteError = signal('');
  readonly previewXml = signal('');
  readonly previewError = signal('');
  readonly submitting = signal(false);
  readonly submitError = signal('');

  readonly rail = computed<Rail>(() =>
    this.rails.find(r => r.code === this.paymentType) ?? this.rails[0],
  );

  ngOnInit(): void {

    /*
     * A teller acts for the bank, so the picker lists every account rather than
     * only their own.
     */
    this.accountService.getAllAccounts().subscribe({
      next: accounts => {
        this.accounts.set(accounts.filter(a => a.status === 'ACTIVE'));
        this.loadingAccounts.set(false);
      },
      error: () => this.loadingAccounts.set(false),
    });
  }

  /** Customer accounts only: a teller does not pay out of a suspense account. */
  selectableAccounts(): AccountResponse[] {
    return this.accounts().filter(
      a => a.accountType === 'CURRENT' || a.accountType === 'SAVINGS',
    );
  }

  onRailChange(): void {

    /*
     * Each scheme restricts what it accepts. Snapping to a permitted charge
     * bearer stops the form offering a combination the scheme would reject.
     */
    const permitted = this.rail().chargeBearers;

    if (!permitted.includes(this.chargeBearer)) {
      this.chargeBearer = permitted[0];
    }

    this.clearDerived();
  }

  onAmountChange(): void {
    this.clearDerived();
  }

  private clearDerived(): void {
    this.quote.set(null);
    this.previewXml.set('');
    this.quoteError.set('');
    this.previewError.set('');
    this.submitError.set('');
  }

  isExternal(): boolean {
    return this.rail().external;
  }

  /** Asks the server what this payment costs. */
  getQuote(): void {

    if (!this.amount || this.amount <= 0) {
      this.quoteError.set('Enter an amount first.');
      return;
    }

    this.quoteError.set('');

    this.transactionService
      .quote(this.paymentType, this.currency, this.amount, this.chargeBearer)
      .subscribe({
        next: fee => this.quote.set(fee),
        error: error =>
          this.quoteError.set(this.messageFrom(error, 'Could not price this payment.')),
      });
  }

  /**
   * Shows the message that would actually be sent, already checked against the
   * KIPS schema. A rejection here is a rejection before any money moves.
   */
  preview(): void {

    this.previewError.set('');
    this.previewXml.set('');

    this.transactionService.previewMessage(this.buildRequest()).subscribe({
      next: xml => this.previewXml.set(xml),
      error: error =>
        this.previewError.set(
          this.messageFrom(error, 'Could not build the scheme message.'),
        ),
    });
  }

  submit(): void {

    this.submitError.set('');
    this.submitting.set(true);

    this.transactionService.createTransfer(this.buildRequest()).subscribe({
      next: transaction => {
        this.submitting.set(false);

        /*
         * What happened next is the whole point of the message: a payment over
         * the four-eyes threshold has not been sent, it is waiting for someone
         * else, and saying "sent" would be a lie the user acts on.
         */
        if (transaction.status === 'PENDING_APPROVAL') {
          this.toasts.info(
            `Held for approval — ${transaction.amount} ${transaction.currency}`,
            'Above the four-eyes threshold. A second person must release it.',
          );
        } else {
          this.toasts.success(
            `Payment ${transaction.status.toLowerCase()} — `
            + `${transaction.amount} ${transaction.currency}`,
            `Reference ${transaction.transactionReference}.`,
          );
        }

        this.router.navigate(['/payments', transaction.transactionReference]);
      },
      error: error => {
        this.submitting.set(false);
        this.submitError.set(
          this.messageFrom(error, 'The payment could not be made.'),
        );
        this.toasts.failure('The payment could not be made', error);
      },
    });
  }

  private buildRequest(): TransferRequest {

    /*
     * End-to-end and instruction identifiers travel with the payment for its
     * whole life, so they are generated per instruction rather than reused.
     */
    const stamp = Date.now().toString(36).toUpperCase();

    return {
      sourceAccountId: this.sourceAccountId!,
      destinationAccountId: this.isExternal() ? null : this.destinationAccountId,
      amount: this.amount!,
      currency: this.currency,
      endToEndId: `E2E-${stamp}`,
      instructionId: `INSTR-${stamp}`,
      paymentType: this.paymentType,
      chargeBearer: this.chargeBearer,
      purposeCode: this.purposeCode === '' ? null : this.purposeCode,
      remittanceInformation: this.remittanceInformation || null,
      debtorName: this.debtorName || null,
      creditorName: this.creditorName || null,
      creditorIban: this.creditorIban || null,
      creditorAgentBic: this.creditorAgentBic || null,
    };
  }

  canSubmit(): boolean {

    if (!this.sourceAccountId || !this.amount || this.amount <= 0) {
      return false;
    }

    return this.isExternal()
      ? !!(this.creditorIban && this.creditorName && this.creditorAgentBic)
      : !!this.destinationAccountId;
  }

  /**
   * Server messages explain the banking reason a payment was refused — an
   * unpermitted charge bearer, a schema breach, insufficient funds — so they
   * are shown as given rather than replaced with something generic.
   */
  private messageFrom(error: unknown, fallback: string): string {

    const body = (error as { error?: unknown })?.error;

    if (typeof body === 'string' && body.trim().startsWith('{')) {
      try {
        return JSON.parse(body).message ?? fallback;
      } catch {
        return fallback;
      }
    }

    const message = (body as { message?: string })?.message;

    return message ?? fallback;
  }
}
