import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, formatNumber } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { AuthService, UserResponse } from '../../core/services/auth';
import {
  AccountResponse,
  AccountService,
  ClientResponse,
  Currency,
} from '../../core/services/account';
import { Onboarding } from './onboarding';
import { MessageView } from '../../shared/message-view';
import { HasUnsavedChanges } from '../../core/guards/unsaved-changes';
import { Toasts } from '../../core/services/toasts';
import { Confirm } from '../../core/services/confirm';
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
import {
  BankDirectoryService,
  CorrespondentBank,
} from '../../core/services/bank-directory';
import {
  CustomerLimits,
  PacsMessage,
  PaymentTypeCode,
  RAILS,
  TransactionResponse,
  TransactionService,
} from '../../core/services/transaction';

/**
 * The customer's own banking.
 *
 * Accounts, the ability to move money between them or out to another bank, and
 * a history of everything that has touched them. Staff see this too, but the
 * payment tools they use live elsewhere — this screen is scoped to whoever is
 * signed in.
 */
@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, DatePipe, FormsModule, Onboarding, MessageView],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, HasUnsavedChanges {

  /**
   * A payment the customer has started but not sent.
   *
   * Nothing is at stake unless the form is actually open, so a closed one
   * never asks — the fields keep their values behind it and are cleared on
   * send anyway.
   */
  hasUnsavedChanges(): boolean {

    if (!this.showForm() || this.sending()) {
      return false;
    }

    return !!(this.amount
      || this.creditorName.trim()
      || this.ibanRest.trim()
      || this.remittanceInformation.trim()
      || this.destinationAccountId);
  }


  private readonly authService = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly toasts = inject(Toasts);
  private readonly bankDirectory = inject(BankDirectoryService);
  private readonly confirm = inject(Confirm);

  readonly banks = signal<CorrespondentBank[]>([]);

  readonly user = signal<UserResponse | null>(null);
  readonly client = signal<ClientResponse | null>(null);
  readonly accounts = signal<AccountResponse[]>([]);
  readonly history = signal<TransactionResponse[]>([]);
  readonly limits = signal<CustomerLimits | null>(null);

  readonly loading = signal(true);
  readonly errorMessage = signal('');

  // --- send money ---

  readonly showForm = signal(false);
  readonly sending = signal(false);
  readonly sendError = signal('');
  readonly sendSuccess = signal('');

  paymentType: PaymentTypeCode = 'INTERNAL';
  sourceAccountId: number | null = null;
  destinationAccountId: number | null = null;
  amount: number | null = null;
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
  /** The rail decides where the money may go, so the country follows it. */
  onRailChange(): void {
    this.snapCountryToRail();
  }

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



  remittanceInformation = '';

  ngOnInit(): void {

    forkJoin({
      user: this.authService.getCurrentUser(),

      // A freshly registered user has no client profile yet, so a 404 here
      // means "not onboarded", not a failure.
      client: this.accountService.getMyClient().pipe(
        catchError(error => (error.status === 404 ? of(null) : of(undefined))),
      ),

      accounts: this.accountService.getMyAccounts().pipe(
        // A closed account is history; it holds nothing and takes no payments.
        map(accounts => (accounts ?? []).filter(a => a.status !== 'CLOSED')),
        catchError(() => of([] as AccountResponse[])),
      ),

      history: this.transactionService.myTransactions().pipe(
        catchError(() => of([] as TransactionResponse[])),
      ),

      limits: this.transactionService.myPaymentLimits().pipe(
        catchError(() => of(null as CustomerLimits | null)),
      ),
    }).subscribe({
      next: ({ user, client, accounts, history, limits }) => {
        this.user.set(user);
        this.client.set(client ?? null);
        this.accounts.set(accounts);
        this.history.set(history);
        this.limits.set(limits);
        this.loading.set(false);

        this.bankDirectory.selectable().subscribe({
          next: banks => this.banks.set(banks),
          error: () => { /* the form warns when the list is empty */ },
        });

        if (client === undefined) {
          this.errorMessage.set('Unable to load your client profile.');
        }
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Unable to load your dashboard.');
      },
    });
  }

  /**
   * Balances are only additive within a single currency, so the dashboard
   * reports one total per currency rather than a single mixed-currency figure.
   */
  totalsByCurrency(): { currency: Currency; total: number }[] {

    const totals = new Map<Currency, number>();

    for (const account of this.accounts()) {
      totals.set(
        account.currency,
        (totals.get(account.currency) ?? 0) + Number(account.balance),
      );
    }

    return [...totals].map(([currency, total]) => ({ currency, total }));
  }

  // --- recent activity ---

  /**
   * How much history is on screen at once.
   *
   * A statement is where you go to read everything; this is the "what has
   * happened lately" list, and a hundred rows of it answers a question nobody
   * asked while burying the two that matter.
   */
  private static readonly RECENT = 8;

  readonly showAllHistory = signal(false);

  visibleHistory(): TransactionResponse[] {

    return this.showAllHistory()
      ? this.history()
      : this.history().slice(0, Dashboard.RECENT);
  }

  hiddenCount(): number {
    return Math.max(0, this.history().length - Dashboard.RECENT);
  }

  toggleHistory(): void {

    this.showAllHistory.set(!this.showAllHistory());

    // A collapsed list must not leave an expanded message stranded below it.
    this.openMessageFor.set(null);
    this.messageXml.set('');
  }

  // --- the message behind a payment ---

  readonly openMessageFor = signal<string | null>(null);
  readonly messageXml = signal('');
  readonly messageError = signal('');

  /**
   * Shows what was actually sent on the customer's behalf.
   *
   * A payment that leaves the bank becomes an ISO 20022 message, and it is the
   * customer's own money — there is no reason to keep the record of it from
   * them. An internal transfer has none, so nothing is offered for one.
   */
  toggleMessage(transaction: TransactionResponse): void {

    const reference = transaction.transactionReference;

    if (this.openMessageFor() === reference) {
      this.openMessageFor.set(null);
      this.messageXml.set('');
      return;
    }

    this.openMessageFor.set(reference);
    this.messageXml.set('');
    this.messageError.set('');

    this.transactionService.myMessages(reference).subscribe({
      next: messages => {

        /*
         * The credit transfer is the payment itself, whichever way it went —
         * ours going out, or theirs coming in. Picking by direction instead
         * would show the status report we sent back for money we received,
         * which answers a question nobody asked.
         */
        const payment = messages.find(m => m.messageType === 'PACS_008')
          ?? messages[0];

        if (!payment) {
          this.messageError.set('No scheme message was sent for this payment.');
          return;
        }

        this.transactionService.myMessageXml(payment.messageId).subscribe({
          next: xml => this.messageXml.set(xml),
          error: () => this.messageError.set('The message could not be loaded.'),
        });
      },
      error: () => this.messageError.set('The message could not be loaded.'),
    });
  }

  /** Only a payment that left the bank has a scheme message behind it. */
  hasMessage(transaction: TransactionResponse): boolean {
    return transaction.paymentType !== 'INTERNAL'
      && transaction.transactionType === 'TRANSFER';
  }

  /**
   * A client the branch has not checked yet. They can see the screen, but
   * nothing can be opened or paid until a teller approves them, so the
   * dashboard says so instead of showing tools that would only 403.
   */
  awaitingApproval(): boolean {
    return this.client()?.status === 'PENDING';
  }

  /** Approved, and with somewhere for the money to come from. */
  canBank(): boolean {
    return this.client()?.status === 'ACTIVE' && this.accounts().length > 0;
  }

  /** Only the rails self-service permits. */
  availableRails() {
    const permitted = this.limits()?.permittedRails ?? ['INTERNAL'];
    return RAILS.filter(r => permitted.includes(r.code));
  }

  limitFor(currency: Currency): number | null {
    return this.limits()?.limits?.[currency] ?? null;
  }

  isExternal(): boolean {
    return this.paymentType !== 'INTERNAL';
  }

  selectedCurrency(): Currency {
    return this.accounts().find(a => a.id === this.sourceAccountId)?.currency
      ?? 'EUR';
  }

  toggleForm(): void {
    this.showForm.set(!this.showForm());
    this.sendError.set('');
    this.sendSuccess.set('');
  }

  /*
   * The same rules the server applies. A customer paying their own bills has
   * nobody at a counter to catch a mistyped IBAN for them, so this screen
   * needs the check more than the teller's does, not less.
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

  remittanceError(): string | null {
    return schemeTextProblem(
      this.remittanceInformation, 'The payment reference', 140,
    );
  }

  amountError(): string | null {
    return amountProblem(this.amount);
  }

  canSend(): boolean {

    if (!this.sourceAccountId || !this.amount || this.amount <= 0) {
      return false;
    }

    if (this.amountError()
      || this.creditorNameError()
      || this.remittanceError()
      || (this.isExternal()
        && (this.ibanError() || this.bicError()
          ))) {

      return false;
    }

    return this.isExternal()
      ? !!(this.creditorIban && this.creditorName && this.creditorAgentBic
        )
      : !!this.destinationAccountId;
  }

  /**
   * One look at the payment before it goes.
   *
   * A customer typing an IBAN at home has no teller beside them to read it
   * back. This is that read-back: who the money goes to and how much, in the
   * form they will see it afterwards.
   */
  send(): void {

    const currency = this.selectedCurrency();
    const amount = `${formatNumber(this.amount ?? 0, 'en-US', '1.2-2')} ${currency}`;

    const source = this.accounts().find(a => a.id === this.sourceAccountId);
    const target = this.accounts().find(a => a.id === this.destinationAccountId);

    const facts = [
      { label: 'From', value: source?.iban ?? '—' },
      {
        label: 'To',
        value: this.isExternal()
          ? [this.creditorName, this.creditorIban].filter(Boolean).join(' · ')
          : (target ? [target.clientName, target.iban].filter(Boolean).join(' · ') : '—'),
      },
      { label: 'Amount', value: amount },
    ];

    if (this.remittanceInformation.trim()) {
      facts.push({ label: 'Reference', value: this.remittanceInformation.trim() });
    }

    this.confirm.askThen({
      title: `Send ${amount}?`,
      message: this.isExternal()
        ? 'The money leaves your account now and goes to the other bank. Any '
          + 'charge for this kind of payment is added when it is sent.'
        : 'The money moves between accounts at this bank straight away.',
      facts,
      note: this.isExternal()
        ? 'Check the IBAN and name carefully. Once sent, getting the money back '
          + 'depends on the other bank agreeing to return it.'
        : 'It arrives immediately and cannot be cancelled afterwards.',
      confirmLabel: 'Send',
      cancelLabel: 'Check again',
    }, () => this.doSend());
  }

  private doSend(): void {

    this.sendError.set('');
    this.sendSuccess.set('');
    this.sending.set(true);

    const stamp = Date.now().toString(36).toUpperCase();
    const currency = this.selectedCurrency();

    this.transactionService.payAsCustomer({
      sourceAccountId: this.sourceAccountId!,
      destinationAccountId: this.isExternal() ? null : this.destinationAccountId,
      amount: this.amount!,
      currency,
      endToEndId: `E2E-${stamp}`,
      instructionId: `INSTR-${stamp}`,
      paymentType: this.paymentType,
      creditorName: this.creditorName || null,
      creditorIban: this.creditorIban || null,
      creditorAgentBic: this.creditorAgentBic || null,
      creditorCountry: this.isExternal() ? this.ibanCountry : null,
      remittanceInformation: this.remittanceInformation || null,
    }).subscribe({
      next: transaction => {
        this.sending.set(false);
        this.sendSuccess.set(
          `Payment ${transaction.transactionReference} — ${transaction.status}.`,
        );

        if (transaction.status === 'PENDING_APPROVAL') {
          this.toasts.info(
            `Held for approval — ${transaction.amount} ${transaction.currency}`,
            'The branch will review it before it goes.',
          );
        } else {
          this.toasts.success(
            `Sent ${transaction.amount} ${transaction.currency}`,
            `Reference ${transaction.transactionReference}.`,
          );
        }
        this.resetForm();
        this.reload();
      },
      error: error => {
        this.sending.set(false);
        this.sendError.set(
          error?.error?.message ?? 'The payment could not be made.',
        );
        this.toasts.failure('The payment could not be made', error);
      },
    });
  }

  /**
   * Back to how the form loads, every field.
   *
   * Called only on a payment that went through. One that failed keeps
   * everything, because those fields are what needs correcting.
   */
  private resetForm(): void {
    this.paymentType = 'INTERNAL';
    this.sourceAccountId = null;
    this.destinationAccountId = null;
    this.amount = null;
    this.creditorName = '';
    this.creditorIban = 'XK';
    this.creditorAgentBic = '';
    this.remittanceInformation = '';
    this.sendError.set('');
  }

  private reload(): void {

    this.accountService.getMyAccounts().subscribe({
      next: accounts =>
        this.accounts.set(accounts.filter(a => a.status !== 'CLOSED')),
      error: () => { /* the payment succeeded; a stale balance is not worth an error */ },
    });

    this.transactionService.myTransactions().subscribe({
      next: history => this.history.set(history),
      error: () => { /* as above */ },
    });
  }

  /**
   * Whether a payment took money out of the customer's accounts or brought it
   * in, which is what they actually want to see in a list.
   */
  isOutgoing(transaction: TransactionResponse): boolean {
    return this.accounts().some(a => a.id === transaction.sourceAccountId);
  }

  counterparty(transaction: TransactionResponse): string {

    /*
     * Cash has no counterparty — the other leg is the branch till. Naming it
     * for what happened reads better than "Received" against a deposit the
     * customer made themselves.
     */
    if (transaction.transactionType === 'DEPOSIT') {
      return 'Cash paid in';
    }

    if (transaction.transactionType === 'WITHDRAWAL') {
      return 'Cash withdrawn';
    }

    if (this.isOutgoing(transaction)) {
      return transaction.creditorName
        ?? transaction.creditorIban
        ?? 'Internal transfer';
    }

    return transaction.debtorName ?? transaction.debtorIban ?? 'Received';
  }

  statusTone(status: string): string {

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

  /**
   * A brand new client has no accounts yet, so only the profile needs setting;
   * the rest of the dashboard renders empty until a teller opens one.
   */
  onClientCreated(client: ClientResponse): void {
    this.client.set(client);
    this.errorMessage.set('');
  }

  logout(): void {
    this.authService.logout();
  }
}
