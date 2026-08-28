import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * SUSPENSE, INCOME and VAULT are the bank's own general ledger accounts, not customer
 * products. They never appear in a customer's own list, but staff screens see
 * them, so the type has to admit them.
 */
export type AccountType =
  | 'CURRENT' | 'SAVINGS' | 'SUSPENSE' | 'INCOME' | 'VAULT';
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

  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  postalCode: string | null;
  country: string | null;

  placeOfBirth: string | null;
  countryOfBirth: string | null;
  nationality: string | null;
  countryOfResidence: string | null;

  identityDocumentType: string | null;
  /** Masked to its last four characters by the server. */
  identityDocumentNumber: string | null;
  identityDocumentCountry: string | null;
  identityDocumentExpiry: string | null;

  politicallyExposed: boolean;
  pepDetails: string | null;
  sourceOfFunds: string | null;

  legalForm: string | null;
  dateOfIncorporation: string | null;
  naceCode: string | null;
  beneficialOwners: {
    id: number;
    fullName: string;
    dateOfBirth: string;
    nationality: string | null;
    countryOfResidence: string | null;
    ownershipPercentage: number;
    controlsByOtherMeans: boolean;
    politicallyExposed: boolean;
  }[];

  createdAt: string;
  updatedAt: string;
}

export type IdentityDocumentType =
  | 'PASSPORT' | 'NATIONAL_ID' | 'RESIDENCE_PERMIT';

export type SourceOfFunds =
  | 'SALARY' | 'BUSINESS_INCOME' | 'PENSION' | 'SAVINGS' | 'INVESTMENTS'
  | 'PROPERTY_SALE' | 'INHERITANCE' | 'REMITTANCES' | 'OTHER';

export type LegalForm =
  | 'SOLE_PROPRIETORSHIP' | 'GENERAL_PARTNERSHIP' | 'LIMITED_PARTNERSHIP'
  | 'LIMITED_LIABILITY' | 'JOINT_STOCK' | 'FOREIGN_BRANCH' | 'NGO' | 'OTHER';

/** A human being declared as owning or controlling a company. */
export interface BeneficialOwnerRequest {
  fullName: string;
  dateOfBirth: string;
  nationality?: string;
  countryOfResidence?: string;
  ownershipPercentage: number;
  controlsByOtherMeans: boolean;
  politicallyExposed: boolean;
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

  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  postalCode?: string;
  country?: string;

  placeOfBirth?: string;
  countryOfBirth?: string;
  nationality?: string;
  countryOfResidence?: string;

  identityDocumentType?: IdentityDocumentType;
  identityDocumentNumber?: string;
  identityDocumentCountry?: string;
  identityDocumentExpiry?: string;
  personalNumber?: string;

  politicallyExposed?: boolean;
  pepDetails?: string;
  sourceOfFunds?: SourceOfFunds;
  sourceOfFundsDetail?: string;

  legalForm?: LegalForm;
  dateOfIncorporation?: string;
  naceCode?: string;
  beneficialOwners?: BeneficialOwnerRequest[];
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
