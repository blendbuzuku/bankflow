import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../../core/services/auth';
import { AccountService, ClientResponse } from '../../core/services/account';
import {
  DayClose,
  TransactionResponse,
  TransactionService,
} from '../../core/services/transaction';

/**
 * What a member of staff sees when they sign in.
 *
 * A teller is not a customer of the bank, so showing them the customer
 * dashboard — accounts they do not have, and an invitation to register as a
 * client — is wrong. This is their actual work: who is waiting to be approved,
 * what needs a second pair of eyes, and whether the books balance.
 */
@Component({
  selector: 'app-overview',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './overview.html',
  styleUrl: './overview.css',
})
export class Overview implements OnInit {

  private readonly authService = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly transactionService = inject(TransactionService);

  readonly pending = signal<TransactionResponse[]>([]);
  readonly clients = signal<ClientResponse[]>([]);
  readonly dayStatus = signal<DayClose['trialBalance'] | null>(null);
  readonly reconciled = signal<boolean | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {

    this.transactionService.awaitingApproval().pipe(
      catchError(() => of([] as TransactionResponse[])),
    ).subscribe(pending => {
      this.pending.set(pending);
      this.loading.set(false);
    });

    this.accountService.getAllClients().pipe(
      catchError(() => of([] as ClientResponse[])),
    ).subscribe(clients =>
      // The bank's own entity is on the books but is not a customer.
      this.clients.set(clients.filter(c => !c.internal)));

    /*
     * End-of-day figures are for operations. A teller gets a 403 here, which is
     * not an error worth showing them — the panel simply stays hidden.
     */
    if (this.canSeeBooks()) {

      this.transactionService.trialBalance().pipe(
        catchError(() => of(null)),
      ).subscribe(balance => this.dayStatus.set(balance));

      this.transactionService.reconciliation().pipe(
        catchError(() => of(null)),
      ).subscribe(result => this.reconciled.set(result?.reconciled ?? null));
    }
  }

  who(): string {
    return this.authService.currentUser()?.username ?? '';
  }

  role(): string {
    return this.authService.currentUser()?.role ?? '';
  }

  canApprove(): boolean {
    return this.authService.canApprove();
  }

  canSeeBooks(): boolean {
    return this.authService.canApprove();
  }

  /** Clients registered but not yet checked — nothing can be opened for them. */
  awaitingApproval(): ClientResponse[] {
    return this.clients().filter(c => c.status === 'PENDING');
  }

  activeClients(): number {
    return this.clients().filter(c => c.status === 'ACTIVE').length;
  }

  pendingTotal(): number {
    return this.pending().reduce((sum, t) => sum + Number(t.amount), 0);
  }

  clientName(client: ClientResponse): string {
    return client.clientType === 'BUSINESS'
      ? client.legalName ?? 'Business client'
      : `${client.firstName ?? ''} ${client.lastName ?? ''}`.trim()
        || 'Individual client';
  }

  booksTone(): string {

    if (this.dayStatus() === null || this.reconciled() === null) {
      return 'info';
    }

    return this.dayStatus()!.balanced && this.reconciled() ? 'good' : 'bad';
  }
}
