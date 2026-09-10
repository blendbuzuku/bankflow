import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe, formatNumber } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  TariffRule,
  TariffRuleRequest,
  TransactionService,
} from '../../core/services/transaction';
import { Toasts } from '../../core/services/toasts';
import { Confirm } from '../../core/services/confirm';
import { HasUnsavedChanges } from '../../core/guards/unsaved-changes';

/**
 * What the bank charges.
 *
 * The prices were always read from the table on every payment, so nothing was
 * hard-coded -- but changing a line took a migration, which made the tariff as
 * fixed in practice as if it had been. This is where it stops being.
 *
 * A price is superseded rather than edited. A payment made last Tuesday was
 * priced under last Tuesday's line, and rewriting that line would leave the
 * customer looking at a charge the bank could no longer derive.
 */
@Component({
  selector: 'app-tariff',
  imports: [DecimalPipe, FormsModule],
  templateUrl: './tariff.html',
  styleUrl: './tariff.css',
})
export class Tariff implements OnInit, HasUnsavedChanges {

  /** A line being added or repriced, not yet sent. */
  hasUnsavedChanges(): boolean {

    if (this.busy()) {
      return false;
    }

    return this.adding() || this.amending() !== null;
  }

  private readonly transactions = inject(TransactionService);
  private readonly toasts = inject(Toasts);
  private readonly confirm = inject(Confirm);

  readonly rules = signal<TariffRule[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);

  readonly adding = signal(false);
  readonly amending = signal<number | null>(null);

  readonly rails = [
    { code: 'INTERNAL', label: 'Internal transfer' },
    { code: 'KIPS_ACH', label: 'KIPS retail clearing (ACH)' },
    { code: 'KIPS_RTGS', label: 'KIPS real-time gross settlement' },
    { code: 'INTERNATIONAL', label: 'International (SWIFT)' },
  ];

  readonly currencies = ['EUR', 'USD', 'GBP', 'CHF'];

  form: TariffRuleRequest = this.blank();

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);

    this.transactions.tariff().subscribe({
      next: rules => {
        this.rules.set(rules);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.toasts.failure('The tariff could not be loaded', error);
      },
    });
  }

  private blank(): TariffRuleRequest {
    return {
      ruleCode: '',
      description: '',
      paymentType: 'KIPS_ACH',
      currency: 'EUR',
      minAmount: 0,
      maxAmount: null,
      fixedFee: 0,
      percentageRate: 0,
      minFee: null,
      maxFee: null,
      validFrom: null,
      validTo: null,
    };
  }

  startAdd(): void {
    this.amending.set(null);
    this.form = this.blank();
    this.adding.set(true);
  }

  /**
   * Repricing starts from what the line charges today.
   *
   * Almost every change is one number moving, and retyping the band, the rail
   * and the currency to alter a fee is how the other four get mistyped.
   */
  startAmend(rule: TariffRule): void {

    this.adding.set(false);

    this.form = {
      ruleCode: rule.ruleCode,
      description: rule.description,
      paymentType: rule.paymentType,
      currency: rule.currency,
      minAmount: rule.minAmount,
      maxAmount: rule.maxAmount,
      fixedFee: rule.fixedFee,
      percentageRate: rule.percentageRate,
      minFee: rule.minFee,
      maxFee: rule.maxFee,
      validFrom: null,
      validTo: null,
    };

    this.amending.set(rule.id);
  }

  cancel(): void {
    this.adding.set(false);
    this.amending.set(null);
    this.form = this.blank();
  }

  /** What the form would charge on a payment of this size, priced here. */
  exampleCharge(amount: number): number {

    const percentage = (Number(this.form.percentageRate) || 0) * amount / 100;
    let fee = (Number(this.form.fixedFee) || 0) + percentage;

    if (this.form.minFee != null && fee < Number(this.form.minFee)) {
      fee = Number(this.form.minFee);
    }

    if (this.form.maxFee != null && fee > Number(this.form.maxFee)) {
      fee = Number(this.form.maxFee);
    }

    return fee;
  }

  /** Whether the example amount falls inside the band being edited. */
  inBand(amount: number): boolean {

    const min = Number(this.form.minAmount) || 0;
    const max = this.form.maxAmount;

    return amount >= min && (max == null || amount <= Number(max));
  }

  formError(): string | null {

    if (!this.form.ruleCode.trim()) {
      return 'A tariff line needs a code.';
    }

    if (!this.form.description.trim()) {
      return 'Describe what this charges for. It is what somebody sees when '
        + 'they ask why they were charged.';
    }

    const min = Number(this.form.minAmount);

    if (Number.isNaN(min) || min < 0) {
      return 'The band must start at zero or above.';
    }

    if (this.form.maxAmount != null && Number(this.form.maxAmount) <= min) {
      return 'The band covers no amount at all. Leave the upper limit empty '
        + 'for an open-ended band.';
    }

    for (const [value, label] of [
      [this.form.fixedFee, 'A fixed charge'],
      [this.form.percentageRate, 'A percentage rate'],
      [this.form.minFee, 'A minimum charge'],
      [this.form.maxFee, 'A maximum charge'],
    ] as [number | null | undefined, string][]) {

      if (value != null && Number(value) < 0) {
        return `${label} cannot be negative. A bank does not pay people to `
          + `make payments.`;
      }
    }

    if (this.form.minFee != null && this.form.maxFee != null
      && Number(this.form.minFee) > Number(this.form.maxFee)) {
      return 'The minimum charge is above the maximum, so the two contradict '
        + 'each other.';
    }

    if (this.amending() !== null && !this.form.validFrom) {
      return 'Say when the new price takes effect.';
    }

    return null;
  }

  private money(value: number | string | null | undefined): string {
    return formatNumber(Number(value ?? 0), 'en-US', '1.2-2');
  }

  private railLabel(code: string): string {
    return this.rails.find(r => r.code === code)?.label ?? code;
  }

  /** "0.50 + 0.1%, at least 1.00, at most 25.00" — the rule as a customer meets it. */
  private priceText(): string {

    const f = this.form;
    const parts = [`${this.money(f.fixedFee)}`];

    if (Number(f.percentageRate)) {
      parts.push(`${f.percentageRate}%`);
    }

    let text = parts.join(' + ');

    if (f.minFee != null) {
      text += `, at least ${this.money(f.minFee)}`;
    }

    if (f.maxFee != null) {
      text += `, at most ${this.money(f.maxFee)}`;
    }

    return `${text} ${f.currency}`;
  }

  /**
   * A price is what every customer on this rail pays from the day it takes
   * effect. It is shown once more as a customer would meet it — against a
   * payment of a real size — because a misplaced decimal in a fee field reads
   * as a sensible number until it is multiplied by every payment.
   */
  save(): void {

    if (this.formError()) {
      return;
    }

    const amending = this.amending() !== null;
    const f = this.form;

    const band = f.maxAmount == null
      ? `${this.money(f.minAmount)} and above`
      : `${this.money(f.minAmount)} to ${this.money(f.maxAmount)}`;

    const example = [100, 1000, 10000].find(a => this.inBand(a))
      ?? (Number(f.minAmount) || 0);

    this.confirm.askThen({
      title: amending ? `Change the price of ${f.ruleCode}?` : `Add ${f.ruleCode}?`,
      message: amending
        ? 'The new price replaces the current one from the date below. '
          + 'Payments already made keep the charge they had.'
        : 'This line prices every matching payment from now on.',
      facts: [
        { label: 'Line', value: `${f.ruleCode} — ${f.description}` },
        { label: 'Rail', value: `${this.railLabel(f.paymentType)} · ${f.currency}` },
        { label: 'Payments of', value: `${band} ${f.currency}` },
        { label: 'Charge', value: this.priceText() },
        {
          label: `On ${this.money(example)}`,
          value: `${this.money(this.exampleCharge(example))} ${f.currency}`,
        },
        { label: 'Takes effect', value: f.validFrom ?? 'Now' },
      ],
      note: 'Every customer paying on this rail is charged this price. Check '
        + 'the example — a misplaced decimal is easy to miss in the form.',
      confirmLabel: amending ? 'Change the price' : 'Add tariff line',
      cancelLabel: 'Check again',
    }, () => this.doSave());
  }

  private doSave(): void {

    this.busy.set(true);

    const amendingId = this.amending();

    const call = amendingId !== null
      ? this.transactions.amendTariffRule(amendingId, this.form)
      : this.transactions.addTariffRule(this.form);

    call.subscribe({
      next: rule => {
        this.busy.set(false);
        this.toasts.success(
          amendingId !== null ? 'Price changed' : 'Tariff line added',
          amendingId !== null
            ? `${rule.ruleCode} takes effect on ${rule.validFrom}. The line it `
              + `replaces stays on the record.`
            : `${rule.ruleCode} now prices ${rule.paymentTypeName} payments.`,
        );
        this.cancel();
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('The tariff could not be changed', error);
      },
    });
  }

  retire(rule: TariffRule): void {

    this.confirm.askThen({
      title: `Retire ${rule.ruleCode}?`,
      message: 'It stops pricing new payments. Older payments keep the charge '
        + 'it gave them, and the line stays on the record to explain it.',
      facts: [
        { label: 'Line', value: `${rule.ruleCode} — ${rule.description}` },
        { label: 'Rail', value: `${rule.paymentTypeName} · ${rule.currency}` },
      ],
      note: 'If no other line covers these payments, they will be charged '
        + 'nothing until one does.',
      confirmLabel: 'Retire line',
      cancelLabel: 'Keep it',
      danger: true,
    }, () => this.doRetire(rule));
  }

  private doRetire(rule: TariffRule): void {

    this.busy.set(true);

    this.transactions.retireTariffRule(rule.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.success(
          'Retired',
          `${rule.ruleCode} no longer prices new payments. It stays on the `
            + `record because it explains the charge on older ones.`,
        );
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('That could not be retired', error);
      },
    });
  }

  reinstate(rule: TariffRule): void {

    this.confirm.askThen({
      title: `Put ${rule.ruleCode} back in use?`,
      message: 'It prices matching payments again from now on.',
      facts: [
        { label: 'Line', value: `${rule.ruleCode} — ${rule.description}` },
        { label: 'Rail', value: `${rule.paymentTypeName} · ${rule.currency}` },
      ],
      note: 'Customers paying on this rail start being charged by it straight away.',
      confirmLabel: 'Put back',
      cancelLabel: 'Leave retired',
    }, () => this.doReinstate(rule));
  }

  private doReinstate(rule: TariffRule): void {

    this.busy.set(true);

    this.transactions.reinstateTariffRule(rule.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.success('Back in use', `${rule.ruleCode} prices again.`);
        this.load();
      },
      error: error => {
        this.busy.set(false);
        this.toasts.failure('That could not be put back', error);
      },
    });
  }

  /**
   * Whether this line prices a payment made today.
   *
   * Not the same as being active. A line that has been superseded stays
   * active until the day its successor takes over -- that is how a payment
   * made this afternoon is still charged this morning's price -- so grouping
   * on the flag alone would show tomorrow's price as though it applied now.
   */
  private effective(rule: TariffRule): boolean {

    if (!rule.active) {
      return false;
    }

    const today = new Date().toISOString().slice(0, 10);

    return rule.validFrom <= today
      && (rule.validTo == null || rule.validTo >= today);
  }

  activeRules(): TariffRule[] {
    return this.rules().filter(r => this.effective(r));
  }

  /** Retired, already expired, or not yet started. */
  retiredRules(): TariffRule[] {
    return this.rules().filter(r => !this.effective(r));
  }

  /** Why a line is not pricing, so the second list is not a mystery. */
  whyInactive(rule: TariffRule): string {

    if (!rule.active) {
      return 'Retired';
    }

    const today = new Date().toISOString().slice(0, 10);

    if (rule.validFrom > today) {
      return `Takes effect ${rule.validFrom}`;
    }

    return `Superseded after ${rule.validTo}`;
  }

  /** How a line prices, in one phrase. */
  pricing(rule: TariffRule): string {

    const parts: string[] = [];

    if (rule.fixedFee > 0) {
      parts.push(`${rule.fixedFee.toFixed(2)} ${rule.currency}`);
    }

    if (rule.percentageRate > 0) {
      parts.push(`${rule.percentageRate}%`);
    }

    if (!parts.length) {
      return 'Free';
    }

    let text = parts.join(' + ');

    if (rule.minFee != null) {
      text += `, at least ${rule.minFee.toFixed(2)}`;
    }

    if (rule.maxFee != null) {
      text += `, capped at ${rule.maxFee.toFixed(2)}`;
    }

    return text;
  }

  band(rule: TariffRule): string {

    return rule.maxAmount == null
      ? `${rule.minAmount.toFixed(2)} and above`
      : `${rule.minAmount.toFixed(2)} – ${rule.maxAmount.toFixed(2)}`;
  }
}
