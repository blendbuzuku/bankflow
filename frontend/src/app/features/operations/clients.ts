import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  AccountResponse,
  AccountService,
  AccountType,
  ClientResponse,
  Currency,
} from '../../core/services/account';
import { TransactionService } from '../../core/services/transaction';
import { HasUnsavedChanges } from '../../core/guards/unsaved-changes';
import { Toasts } from '../../core/services/toasts';

/**
 * The counter: clients, their accounts, and cash in and out.
 *
 * Opening an account and handling cash are things only staff can do, so they
 * live here rather than on the customer's own dashboard. Cash is booked as a
 * proper double-entry transaction — adjusting a balance directly would move
 * money with no ledger entry behind it, which end-of-day reconciliation
 * correctly reports as a break.
 */
@Component({
  selector: 'app-clients',
  imports: [DecimalPipe, FormsModule],
  templateUrl: './clients.html',
  styleUrl: './clients.css',
})
export class Clients implements OnInit, HasUnsavedChanges {

  /** A half-filled account or cash form is work that would be lost. */
  hasUnsavedChanges(): boolean {

    if (this.busy()) {
      return false;
    }

    return !!(this.newAccountClientId
      || this.newAccountPurpose.trim()
      || this.cashAccountId
      || this.cashAmount
      || this.cashNarrative.trim());
  }


  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly toasts = inject(Toasts);

  readonly clients = signal<ClientResponse[]>([]);
  readonly accounts = signal<AccountResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');

  readonly busy = signal(false);

  /** Closed accounts are history — kept, but folded away by default. */
  readonly showClosed = signal(new Set<number>());

  // open an account
  newAccountClientId: number | null = null;
  newAccountType: AccountType = 'CURRENT';
  newAccountCurrency: Currency = 'EUR';
  newAccountPurpose = '';

  // cash
  cashAccountId: number | null = null;
  cashAmount: number | null = null;
  cashNarrative = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);
    this.error.set('');

    this.accountService.getAllClients().subscribe({
      next: clients => {
        // The bank's own entity is on the books but is not a customer.
        this.clients.set(clients.filter(c => !c.internal));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load clients.');
        this.toasts.error('Could not load clients', 'The service may be down.');
        this.loading.set(false);
      },
    });

    this.accountService.getAllAccounts().subscribe({
      next: accounts => this.accounts.set(accounts),
      error: () => this.accounts.set([]),
    });
  }

  /**
   * Customer accounts that can still take money: the bank's own ledger
   * accounts are not products, and a closed account takes no payments.
   */
  customerAccounts(): AccountResponse[] {
    return this.accounts().filter(
      a => (a.accountType === 'CURRENT' || a.accountType === 'SAVINGS')
        && a.status !== 'CLOSED',
    );
  }

  /** What the client actually holds. */
  accountsOf(clientId: number): AccountResponse[] {
    return this.accounts()
      .filter(a => a.clientId === clientId && a.status !== 'CLOSED');
  }

  closedAccountsOf(clientId: number): AccountResponse[] {
    return this.accounts()
      .filter(a => a.clientId === clientId && a.status === 'CLOSED');
  }

  isShowingClosed(clientId: number): boolean {
    return this.showClosed().has(clientId);
  }

  toggleClosed(clientId: number): void {

    const next = new Set(this.showClosed());

    if (!next.delete(clientId)) {
      next.add(clientId);
    }

    this.showClosed.set(next);
  }

  isClosed(account: AccountResponse): boolean {
    return account.status === 'CLOSED';
  }

  /** Only an empty account can be closed, so the button follows the rule. */
  canClose(account: AccountResponse): boolean {
    return !this.isClosed(account) && Number(account.balance) === 0;
  }

  close(account: AccountResponse): void {

    this.busy.set(true);
    this.error.set('');
    this.notice.set('');

    this.accountService.closeAccount(account.id).subscribe({
      next: closed => {
        this.busy.set(false);
        this.notice.set(`${closed.iban} closed.`);
        this.toasts.success(
          'Account closed',
          `${closed.iban} takes no further payments. Its history is kept.`,
        );
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.error.set(
          error?.error?.message ?? 'The account could not be closed.',
        );
        this.toasts.failure('The account could not be closed', error);
      },
    });
  }

  clientName(client: ClientResponse): string {
    return client.clientType === 'BUSINESS'
      ? client.legalName ?? 'Business client'
      : `${client.firstName ?? ''} ${client.lastName ?? ''}`.trim()
        || 'Individual client';
  }

  isPending(client: ClientResponse): boolean {
    return client.status === 'PENDING';
  }

  /**
   * Approving a client is what lets accounts be opened for them, so it is the
   * first thing a teller does after someone registers.
   */
  activate(client: ClientResponse): void {

    this.busy.set(true);
    this.error.set('');
    this.notice.set('');

    this.accountService.activateClient(client.id).subscribe({
      next: updated => {
        this.busy.set(false);
        this.notice.set(`${this.clientName(updated)} approved.`);
        this.toasts.success(
          `${this.clientName(updated)} approved`,
          'An account can now be opened for them.',
        );
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.error.set(
          error?.error?.message ?? 'The client could not be approved.',
        );
        this.toasts.failure('The client could not be approved', error);
      },
    });
  }

  /** Only approved clients can have accounts opened for them. */
  approvedClients(): ClientResponse[] {
    return this.clients().filter(c => c.status === 'ACTIVE');
  }

  statusTone(status: string): string {

    switch (status) {
      case 'ACTIVE': return 'good';
      case 'PENDING': return 'warn';
      default: return 'bad';
    }
  }

  openAccount(): void {

    if (!this.newAccountClientId) {
      return;
    }

    this.busy.set(true);
    this.error.set('');
    this.notice.set('');

    this.accountService.createAccount({
      clientId: this.newAccountClientId,
      accountType: this.newAccountType,
      currency: this.newAccountCurrency,
      purpose: this.newAccountPurpose.trim() || null,
    }).subscribe({
      next: account => {
        this.busy.set(false);
        this.notice.set(
          `Opened ${account.accountType} ${account.currency} — ${account.iban}`,
        );
        this.toasts.success(
          `${account.accountType} ${account.currency} account opened`,
          `${account.iban} for ${account.clientName}.`,
        );
        this.newAccountPurpose = '';
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.error.set(
          error?.error?.message ?? 'The account could not be opened.',
        );
        this.toasts.failure('The account could not be opened', error);
      },
    });
  }

  cash(kind: 'deposit' | 'withdraw'): void {

    if (!this.cashAccountId || !this.cashAmount) {
      return;
    }

    this.busy.set(true);
    this.error.set('');
    this.notice.set('');

    const call = kind === 'deposit'
      ? this.transactionService.deposit(
          this.cashAccountId, this.cashAmount, this.cashNarrative)
      : this.transactionService.withdraw(
          this.cashAccountId, this.cashAmount, this.cashNarrative);

    call.subscribe({
      next: transaction => {
        this.busy.set(false);
        this.notice.set(
          `${kind === 'deposit' ? 'Deposited' : 'Withdrew'} `
          + `${transaction.amount} ${transaction.currency} — `
          + `${transaction.transactionReference}`,
        );
        this.toasts.success(
          `${kind === 'deposit' ? 'Cash paid in' : 'Cash paid out'}: `
          + `${transaction.amount} ${transaction.currency}`,
          `Booked as ${transaction.transactionReference}.`,
        );
        this.cashAmount = null;
        this.cashNarrative = '';
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.error.set(
          error?.error?.message ?? 'The cash operation failed.',
        );
        this.toasts.failure(
          kind === 'deposit' ? 'The deposit failed' : 'The withdrawal failed',
          error,
        );
      },
    });
  }
}
