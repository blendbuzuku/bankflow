import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe, formatNumber } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Toasts } from '../../core/services/toasts';
import { Confirm } from '../../core/services/confirm';
import { WorkQueues } from '../../core/services/work-queues';
import {
  COUNTRIES_COMMON,
  COUNTRIES_REST,
} from '../../core/validation/countries';
import { IBAN_LENGTHS } from '../../core/validation/iban';

import {
  amountProblem,
  bicProblem,
  ibanProblem,
  schemeTextProblem,
  wrongCountryForRail,
} from '../../core/validation/iban';
import { HasUnsavedChanges } from '../../core/guards/unsaved-changes';
import {
  BankDirectoryService,
  CorrespondentBank,
} from '../../core/services/bank-directory';
import { MessageView } from '../../shared/message-view';

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
  imports: [FormsModule, DecimalPipe, MessageView],
  templateUrl: './payment-form.html',
  styleUrl: './payment-form.css',
})
export class PaymentForm implements OnInit, HasUnsavedChanges {

  /**
   * Whether leaving would throw away work.
   *
   * Only the fields somebody had to type count. A rail and a currency arrive
   * with defaults, so treating them as entered would question every
   * navigation and teach people to dismiss the warning unread.
   */
  hasUnsavedChanges(): boolean {

    if (this.submitting()) {
      return false;
    }

    return !!(this.amount
      || this.creditorName.trim()
      || this.ibanRest.trim()
      || this.debtorName.trim()
      || this.remittanceInformation.trim()
      || this.sourceAccountId
      || this.destinationAccountId);
  }

  /**
   * Puts the form back exactly as it loads.
   *
   * Every field, not a chosen subset: after a payment has gone, what is left
   * on screen belongs to a payment that no longer needs typing. Leaving some
   * of it -- the rail, the currency -- would mean the next payment starts
   * half-filled with the last one's answers, which is how somebody sends
   * RTGS when they meant ACH.
   *
   * Kept beside the check above because the two are one decision about what
   * counts as work in progress. When they were apart they drifted, and a
   * payment that had just been sent successfully was announced as unsaved
   * work about to be lost.
   *
   * Only ever called after a success. A failed payment leaves everything
   * untouched, because the fields are what the person needs to correct.
   */
  private resetForm(): void {

    this.paymentType = 'KIPS_ACH';
    this.sourceAccountId = null;
    this.destinationAccountId = null;
    this.amount = null;
    this.currency = 'EUR';
    this.chargeBearer = 'SLEV';
    this.purposeCode = '';
    this.remittanceInformation = '';
    this.debtorName = '';
    this.creditorName = '';
    this.creditorIban = 'XK';
    this.creditorAgentBic = '';

    // The price and the message belonged to the payment that has now gone.
    this.quote.set(null);
    this.quoteError.set('');
    this.previewXml.set('');
    this.previewError.set('');
    this.submitError.set('');
  }

  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);
  private readonly queues = inject(WorkQueues);
  private readonly bankDirectory = inject(BankDirectoryService);
  private readonly confirm = inject(Confirm);

  readonly banks = signal<CorrespondentBank[]>([]);

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
  creditorIban = 'XK';
  creditorAgentBic = '';

  /*
   * Where the money is going, picked rather than typed.
   *
   * Unlike an IBAN a country code carries no checksum, so a wrong-but-real
   * code is indistinguishable from a right one and nothing downstream can
   * catch it. It goes into the beneficiary's PstlAdr and is what screening a
   * destination country has to run on.
   */

  /*
   * The IBAN's country is chosen from the list; the rest is typed.
   *
   * Those two characters are the one part of an IBAN nothing protects. A wrong
   * digit anywhere else fails the mod-97 check, but type DE where you meant DK
   * and both are real countries -- the checksum is satisfied and the payment
   * is simply addressed to the wrong place. So they are set by the dropdown
   * and shown fixed on the field, where they cannot be typed over.
   *
   * Accessors over the one creditorIban value rather than two more pieces of
   * state, so validation, reset and the request body all keep working from a
   * single source and cannot drift from what is on screen.
   */
  get ibanCountry(): string {
    return this.creditorIban.slice(0, 2).toUpperCase();
  }

  set ibanCountry(code: string) {
    this.creditorIban = (code ?? '').toUpperCase() + this.creditorIban.slice(2);
  }

  get ibanRest(): string {
    return this.creditorIban.slice(2);
  }

  set ibanRest(rest: string) {
    this.creditorIban =
      this.ibanCountry + (rest ?? '').toUpperCase().replace(/\s/g, '');
  }

  /** How much of an IBAN of the chosen country is still missing. */
  ibanRemaining(): number {

    const expected = IBAN_LENGTHS[this.ibanCountry];

    return expected ? expected - this.creditorIban.length : 0;
  }


  /**
   * Whether this rail can reach another country at all.
   *
   * KIPS clears Kosovo, so on either KIPS rail the beneficiary country is not
   * a question with more than one answer -- offering two hundred of them is
   * offering a hundred and ninety-nine ways to be wrong about something the
   * rail already decided.
   */
  foreignAllowed(): boolean {
    return this.paymentType === 'INTERNATIONAL';
  }


  /**
   * Keeps the IBAN's country honest when the rail changes.
   *
   * Picking Germany for an international payment and then switching to KIPS
   * would otherwise leave DE sitting at the head of the IBAN on a rail that
   * cannot carry it.
   */
  private snapCountryToRail(): void {

    if (!this.foreignAllowed() && this.ibanCountry !== 'XK') {
      this.ibanCountry = 'XK';
    }
  }

  readonly commonCountries = COUNTRIES_COMMON;
  readonly otherCountries = COUNTRIES_REST;




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

    this.bankDirectory.selectable().subscribe({
      next: banks => this.banks.set(banks),
      error: () => this.toasts.error(
        'Could not load the bank directory',
        'A BIC cannot be chosen until it loads.',
      ),
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

    this.snapCountryToRail();
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

  /**
   * Prices the payment, then asks.
   *
   * The charge is fetched rather than taken from a quote the user may or may
   * not have asked for, because the question is only worth asking if it shows
   * the whole of what leaves the account — and a figure without the charge is
   * not that.
   */
  submit(): void {

    this.submitError.set('');

    if (!this.amount || this.amount <= 0) {
      this.ask(null);
      return;
    }

    this.transactionService
      .quote(this.paymentType, this.currency, this.amount, this.chargeBearer)
      .subscribe({
        next: fee => {
          this.quote.set(fee);
          this.ask(fee);
        },

        /*
         * A price that could not be fetched is a line missing from the
         * question, not a reason to stop — the server prices the payment
         * again when it books it.
         */
        error: () => this.ask(null),
      });
  }

  private ask(fee: FeeAssessment | null): void {

    const money = (value: number) =>
      `${formatNumber(value, 'en-US', '1.2-2')} ${this.currency}`;

    const source = this.accounts().find(a => a.id === this.sourceAccountId);
    const target = this.accounts().find(a => a.id === this.destinationAccountId);

    const describe = (a: AccountResponse | undefined) =>
      a ? [a.clientName, a.iban].filter(Boolean).join(' · ') : '—';

    const to = this.isExternal()
      ? [this.creditorName, this.creditorIban, this.creditorAgentBic]
        .filter(Boolean).join(' · ')
      : describe(target);

    const facts = [
      { label: 'From', value: describe(source) },
      { label: 'To', value: to || '—' },
      { label: 'Amount', value: money(this.amount ?? 0) },
      { label: 'Rail', value: this.rail().label },
    ];

    if (fee) {
      facts.push({
        label: 'Charge',
        value: fee.debtorFee > 0 ? money(fee.debtorFee) : 'None to the sender',
      });
      facts.push({
        label: 'Total debited',
        value: money((this.amount ?? 0) + fee.debtorFee),
      });
    }

    if (this.remittanceInformation.trim()) {
      facts.push({ label: 'Reference', value: this.remittanceInformation.trim() });
    }

    this.confirm.askThen({
      title: `Send ${money(this.amount ?? 0)}?`,
      message: this.isExternal()
        ? 'The sender is debited now and the payment goes to KIPS. A payment '
          + 'over the approval threshold waits for a second person instead.'
        : 'An on-us transfer settles on our own books straight away.',
      facts,
      note: this.isExternal()
        ? 'Once it reaches the scheme it cannot be stopped. Getting it back '
          + 'takes a recall, which the other bank may refuse.'
        : 'It settles immediately. Reversing it takes a new transfer the '
          + 'other way.',
      confirmLabel: 'Send payment',
      cancelLabel: 'Check again',
    }, () => this.send());
  }

  private send(): void {

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

        /*
         * Cleared before navigating, not after: the guard runs during the
         * navigation, and a form still full of a payment that has already
         * gone would stop the very screen showing it was sent.
         */
        this.resetForm();

        // A sent payment joins the queue the scheme has to answer.
        this.queues.refresh();

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
      creditorCountry: this.isExternal() ? this.ibanCountry : null,
    };
  }

  /*
   * The same rules the server applies, checked while the field still has the
   * cursor in it. The server stays the authority and repeats every one of
   * them -- an IBAN that only the browser checked is an IBAN nobody checked.
   *
   * Each returns null when there is nothing wrong, so the template can show
   * the reason directly rather than mapping a boolean back to an explanation.
   */

  ibanError(): string | null {

    /*
     * A country with nothing after it is an untouched field, not a bad IBAN.
     * The country is preset, so without this the form opens already
     * complaining about a number nobody has started typing.
     */
    if (!this.ibanRest.trim()) {
      return null;
    }

    return ibanProblem(this.creditorIban)
      ?? wrongCountryForRail(this.creditorIban, this.paymentType);
  }

  bicError(): string | null {
    return bicProblem(this.creditorAgentBic);
  }

  creditorNameError(): string | null {
    return schemeTextProblem(this.creditorName, 'The beneficiary name', 70);
  }

  debtorNameError(): string | null {
    return schemeTextProblem(this.debtorName, 'The payer name', 70);
  }

  remittanceError(): string | null {
    return schemeTextProblem(
      this.remittanceInformation, 'The payment reference', 140,
    );
  }

  amountError(): string | null {
    return amountProblem(this.amount);
  }

  /** Anything that would be refused, so submit can be held back. */
  private anyFieldError(): boolean {

    return !!(this.amountError()
      || this.creditorNameError()
      || this.debtorNameError()
      || this.remittanceError()
      || (this.isExternal()
        && (this.ibanError() || this.bicError()
          )));
  }

  canSubmit(): boolean {

    if (!this.sourceAccountId || !this.amount || this.amount <= 0) {
      return false;
    }

    if (this.anyFieldError()) {
      return false;
    }

    return this.isExternal()
      ? !!(this.creditorIban && this.creditorName && this.creditorAgentBic
        )
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
