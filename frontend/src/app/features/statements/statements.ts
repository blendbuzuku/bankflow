import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AccountResponse, AccountService } from '../../core/services/account';
import { AuthService } from '../../core/services/auth';
import { Toasts } from '../../core/services/toasts';
import {
  StatementResponse,
  TransactionService,
} from '../../core/services/transaction';

/**
 * An account's statement for a period.
 *
 * Staff pick from every account, a customer sees only their own — and the
 * server enforces that regardless, so the narrower list here is a convenience
 * rather than the control.
 */
@Component({
  selector: 'app-statements',
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './statements.html',
  styleUrl: './statements.css',
})
export class Statements implements OnInit {

  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);
  private readonly authService = inject(AuthService);
  private readonly toasts = inject(Toasts);

  readonly accounts = signal<AccountResponse[]>([]);
  readonly statement = signal<StatementResponse | null>(null);

  readonly loading = signal(false);
  readonly loadingAccounts = signal(true);
  readonly error = signal('');

  readonly xml = signal('');
  readonly showXml = signal(false);

  accountId: number | null = null;
  from = '';
  to = '';

  ngOnInit(): void {

    // A calendar month back is what people actually want to see.
    const today = new Date();
    const monthAgo = new Date(today);
    monthAgo.setMonth(monthAgo.getMonth() - 1);

    this.to = this.asDate(today);
    this.from = this.asDate(monthAgo);

    const source = this.authService.isStaff()
      ? this.accountService.getAllAccounts()
      : this.accountService.getMyAccounts();

    source.subscribe({
      next: accounts => {
        const usable = accounts.filter(
          a => (a.accountType === 'CURRENT' || a.accountType === 'SAVINGS')
            && a.status !== 'CLOSED',
        );

        this.accounts.set(usable);
        this.accountId = usable[0]?.id ?? null;
        this.loadingAccounts.set(false);

        if (this.accountId) {
          this.load();
        }
      },
      error: () => {
        this.loadingAccounts.set(false);
        this.toasts.error('Could not load accounts');
      },
    });
  }

  private asDate(value: Date): string {
    return value.toISOString().slice(0, 10);
  }

  load(): void {

    if (!this.accountId) {
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.showXml.set(false);
    this.xml.set('');

    this.transactionService
      .statement(this.accountId, this.from, this.to)
      .subscribe({
        next: statement => {
          this.statement.set(statement);
          this.loading.set(false);
        },
        error: error => {
          this.loading.set(false);
          this.statement.set(null);
          this.error.set(
            error?.error?.message ?? 'The statement could not be produced.',
          );
          this.toasts.failure('The statement could not be produced', error);
        },
      });
  }

  /** The ISO form, checked against the scheme's schema before it is returned. */
  toggleXml(): void {

    if (this.showXml()) {
      this.showXml.set(false);
      return;
    }

    if (this.xml()) {
      this.showXml.set(true);
      return;
    }

    if (!this.accountId) {
      return;
    }

    this.transactionService
      .statementXml(this.accountId, this.from, this.to)
      .subscribe({
        next: xml => {
          this.xml.set(xml);
          this.showXml.set(true);
        },
        error: error =>
          this.toasts.failure('The camt.053 could not be produced', error),
      });
  }

  label(account: AccountResponse): string {

    const purpose = account.purpose ? ` (${account.purpose})` : '';

    return `${account.iban} — ${account.accountType}${purpose} ${account.currency}`;
  }

  /** True when the period had no movement at all. */
  isEmpty(): boolean {
    return this.statement()?.entries.length === 0;
  }
}
