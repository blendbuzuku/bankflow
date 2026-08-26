import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  detail?: string;
}

/**
 * Tells the user what just happened.
 *
 * Banking work is a sequence of consequential actions — money moved, a client
 * approved, a payment released — and every one of them has to land visibly.
 * An action that silently succeeds reads as an action that did nothing, and
 * the usual response to that is to press the button again, which is how you
 * end up with eight current accounts.
 *
 * Errors stay until dismissed. A failure the user did not read is a failure
 * they think did not happen.
 */
@Injectable({ providedIn: 'root' })
export class Toasts {

  private nextId = 1;

  readonly toasts = signal<Toast[]>([]);

  /** Something worked. Auto-dismisses. */
  success(title: string, detail?: string): void {
    this.push('success', title, detail, 5000);
  }

  /** Something failed. Stays until the user dismisses it. */
  error(title: string, detail?: string): void {
    this.push('error', title, detail, 0);
  }

  info(title: string, detail?: string): void {
    this.push('info', title, detail, 5000);
  }

  /**
   * Reports a failed call, preferring the server's own message.
   *
   * The backend already explains itself well — "Arta Krasniqi is PENDING. A
   * client must be approved before…" — so the fallback is only for a call that
   * never reached it.
   */
  failure(fallback: string, error: unknown): void {

    const message = (error as { error?: { message?: string } })?.error?.message;

    this.error(fallback, message ?? undefined);
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  clear(): void {
    this.toasts.set([]);
  }

  private push(
    kind: ToastKind,
    title: string,
    detail: string | undefined,
    timeout: number,
  ): void {

    const id = this.nextId++;

    this.toasts.update(list => [...list, { id, kind, title, detail }]);

    if (timeout > 0) {
      setTimeout(() => this.dismiss(id), timeout);
    }
  }
}
