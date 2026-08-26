import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * SUSPENSE and INCOME are the bank's own general ledger accounts, not customer
 * products. They never appear in a customer's own list, but staff screens see
 * them, so the type has to admit them.
 */
export type AccountType = 'CURRENT' | 'SAVINGS' | 'SUSPENSE' | 'INCOME';
export type AccountStatus = 'ACTIVE' | 'BLOCKED' | 'CLOSED';
export type Currency = 'EUR' | 'USD' | 'GBP' | 'CHF';
export type ClientType = 'INDIVIDUAL' | 'BUSINESS';

export interface AccountResponse {
  id: number;
  clientId: number;
  clientName: string | null;
  clientStatus: string | null;
  accountNumber: string;
  iban: string;
  accountType: AccountType;

  /** What the account is for, when a client holds more than one. */
  purpose: string | null;

  currency: Currency;
  balance: number;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ClientResponse {
  id: number;
  clientType: ClientType;
  firstName: string | null;
  lastName: string | null;
  email: string;
  phone: string | null;
  dateOfBirth: string | null;
  status: string;

  /** The bank's own entity. Never a customer, never offered as one. */
  internal: boolean;

  legalName: string | null;
  registrationNumber: string | null;
  taxNumber: string | null;
  industry: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ClientCreateRequest {
  clientType: ClientType;
  firstName?: string;
  lastName?: string;
  dateOfBirth?: string;
  legalName?: string;
  registrationNumber?: string;
  taxNumber?: string;
  industry?: string;
  email: string;
  phone?: string;
}

export interface AccountCreateRequest {
  clientId: number;
  accountType: AccountType;
  currency: Currency;

  /**
   * Only needed to open a second account of the same type and currency, where
   * it is what tells the two apart.
   */
  purpose?: string | null;
}

@Service()
export class AccountService {

  private readonly http = inject(HttpClient);

  getMyClient(): Observable<ClientResponse> {
    return this.http.get<ClientResponse>('/api/clients/me');
  }

  /** Every client. Staff only. */
  getAllClients(): Observable<ClientResponse[]> {
    return this.http.get<ClientResponse[]>('/api/clients');
  }

  createClient(
    request: ClientCreateRequest
  ): Observable<ClientResponse> {
    return this.http.post<ClientResponse>('/api/clients', request);
  }

  /** Approves a client after their identity has been checked. */
  activateClient(clientId: number): Observable<ClientResponse> {
    return this.http.post<ClientResponse>(`/api/clients/${clientId}/activate`, {});
  }

  suspendClient(clientId: number): Observable<ClientResponse> {
    return this.http.post<ClientResponse>(`/api/clients/${clientId}/suspend`, {});
  }

  /** Registers a client for someone at the counter, not for the caller. */
  createClientFor(
    userId: number,
    request: ClientCreateRequest,
  ): Observable<ClientResponse> {
    return this.http.post<ClientResponse>(
      `/api/clients/for-user/${userId}`, request);
  }

  getMyAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>('/api/accounts/me');
  }

  /** Every account. Staff only — a teller acts for the bank, not for themselves. */
  getAllAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>('/api/accounts');
  }

  getById(id: number): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`/api/accounts/${id}`);
  }

  createAccount(
    request: AccountCreateRequest
  ): Observable<AccountResponse> {
    return this.http.post<AccountResponse>('/api/accounts', request);
  }

  /**
   * Ends an account's life. Not a delete — the payments behind it stay, and
   * the server refuses an account that still holds a balance.
   */
  closeAccount(accountId: number): Observable<AccountResponse> {
    return this.http.post<AccountResponse>(
      `/api/accounts/${accountId}/close`, {},
    );
  }
}
