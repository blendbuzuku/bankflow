import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Toasts } from '../../core/services/toasts';

import {
  AccountService,
  ClientResponse,
  ClientType,
} from '../../core/services/account';

/**
 * Registering as a bank client.
 *
 * Signing in and being a customer of the bank are different things: the account
 * gets you through the door, this is the KYC record that lets accounts be
 * opened in your name. Until it exists there is nothing to hold money.
 */
@Component({
  selector: 'app-onboarding',
  imports: [FormsModule],
  templateUrl: './onboarding.html',
  styleUrl: './onboarding.css',
})
export class Onboarding {

  private readonly accountService = inject(AccountService);
  private readonly toasts = inject(Toasts);

  /** Emitted so the dashboard can reload once a profile exists. */
  readonly created = output<ClientResponse>();

  clientType: ClientType = 'INDIVIDUAL';

  firstName = '';
  lastName = '';
  dateOfBirth = '';

  legalName = '';
  registrationNumber = '';
  taxNumber = '';
  industry = '';

  email = '';
  phone = '';

  readonly saving = signal(false);
  readonly error = signal('');

  isBusiness(): boolean {
    return this.clientType === 'BUSINESS';
  }

  canSubmit(): boolean {

    if (!this.email) {
      return false;
    }

    return this.isBusiness()
      ? !!(this.legalName && this.registrationNumber)
      : !!(this.firstName && this.lastName);
  }

  submit(): void {

    this.error.set('');
    this.saving.set(true);

    this.accountService.createClient({
      clientType: this.clientType,
      firstName: this.isBusiness() ? undefined : this.firstName,
      lastName: this.isBusiness() ? undefined : this.lastName,
      dateOfBirth: this.isBusiness() || !this.dateOfBirth
        ? undefined
        : this.dateOfBirth,
      legalName: this.isBusiness() ? this.legalName : undefined,
      registrationNumber: this.isBusiness() ? this.registrationNumber : undefined,
      taxNumber: this.isBusiness() ? this.taxNumber || undefined : undefined,
      industry: this.isBusiness() ? this.industry || undefined : undefined,
      email: this.email,
      phone: this.phone || undefined,
    }).subscribe({
      next: client => {
        this.saving.set(false);
        this.toasts.success(
          'Your details have been sent to the branch',
          'Once someone has checked them, an account can be opened for you.',
        );
        this.created.emit(client);
      },
      error: error => {
        this.saving.set(false);
        this.toasts.failure('Your profile could not be created', error);
        this.error.set(
          error?.error?.message ?? 'Your profile could not be created.',
        );
      },
    });
  }
}
