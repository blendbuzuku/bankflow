import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  BankDirectoryService,
  CorrespondentBank,
} from '../../core/services/bank-directory';
import { Toasts } from '../../core/services/toasts';

/**
 * The banks this bank can pay.
 *
 * An entry here decides where money goes, so adding or retiring one is as
 * consequential as releasing a payment — which is why it sits with operations
 * and why every change is on the audit trail.
 */
@Component({
  selector: 'app-banks',
  imports: [FormsModule],
  templateUrl: './banks.html',
  styleUrl: './banks.css',
})
export class Banks implements OnInit {

  private readonly directory = inject(BankDirectoryService);
  private readonly toasts = inject(Toasts);

  readonly banks = signal<CorrespondentBank[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);

  readonly renaming = signal<number | null>(null);

  newBic = '';
  newName = '';
  renameTo = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);

    this.directory.all().subscribe({
      next: banks => {
        this.banks.set(banks);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.toasts.failure('Could not load the bank directory', error);
      },
    });
  }

  /** The country is in the BIC, so it is shown rather than asked for. */
  countryOf(bic: string): string {
    return bic.length >= 6 ? bic.substring(4, 6) : '';
  }

  canAdd(): boolean {
    return /^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$/.test(
      this.newBic.trim().toUpperCase(),
    ) && this.newName.trim().length > 0;
  }

  add(): void {

    const bic = this.newBic.trim().toUpperCase();

    this.busy.set(true);

    this.directory.add(bic, this.newName.trim(), this.countryOf(bic)).subscribe({
      next: bank => {
        this.busy.set(false);
        this.newBic = '';
        this.newName = '';
        this.toasts.success(
          'Bank added',
          `${bank.label} can now be chosen on a payment.`,
        );
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('The bank could not be added', error);
      },
    });
  }

  startRename(bank: CorrespondentBank): void {
    this.renaming.set(bank.id);
    this.renameTo = bank.name;
  }

  cancelRename(): void {
    this.renaming.set(null);
    this.renameTo = '';
  }

  rename(bank: CorrespondentBank): void {

    this.busy.set(true);

    this.directory.rename(bank.id, this.renameTo.trim()).subscribe({
      next: updated => {
        this.busy.set(false);
        this.renaming.set(null);
        this.toasts.success('Renamed', updated.label);
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('The bank could not be renamed', error);
      },
    });
  }

  toggle(bank: CorrespondentBank): void {

    this.busy.set(true);

    const call = bank.active
      ? this.directory.retire(bank.id)
      : this.directory.reinstate(bank.id);

    call.subscribe({
      next: updated => {
        this.busy.set(false);
        this.toasts.success(
          updated.active ? 'Back in use' : 'Retired',
          updated.active
            ? `${updated.bic} can be chosen again.`
            : `${updated.bic} is no longer offered. Payments already sent keep it.`,
        );
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('That could not be changed', error);
      },
    });
  }

  activeBanks(): CorrespondentBank[] {
    return this.banks().filter(b => b.active);
  }

  retiredBanks(): CorrespondentBank[] {
    return this.banks().filter(b => !b.active);
  }
}
