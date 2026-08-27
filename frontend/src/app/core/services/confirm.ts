import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;

  /** Marks the confirming action as destructive, so it reads as one. */
  danger: boolean;
}

/**
 * Asks the user a yes-or-no question and waits for the answer.
 *
 * A native confirm() would do the job, but it cannot be styled, cannot be
 * read by the same conventions as the rest of the screen, and blocks the whole
 * browser while it waits. This behaves the same way from the caller's side —
 * an answer arrives, eventually — without leaving the application.
 */
@Injectable({ providedIn: 'root' })
export class Confirm {

  readonly request = signal<ConfirmRequest | null>(null);

  private answer: Subject<boolean> | null = null;

  ask(request: Partial<ConfirmRequest> & { message: string }): Observable<boolean> {

    /*
     * A second question while one is open would strand the first caller
     * waiting for an answer that can no longer arrive.
     */
    this.answer?.next(false);
    this.answer?.complete();

    this.answer = new Subject<boolean>();

    this.request.set({
      title: request.title ?? 'Are you sure?',
      message: request.message,
      confirmLabel: request.confirmLabel ?? 'Continue',
      cancelLabel: request.cancelLabel ?? 'Cancel',
      danger: request.danger ?? false,
    });

    return this.answer.asObservable();
  }

  respond(confirmed: boolean): void {

    this.request.set(null);

    this.answer?.next(confirmed);
    this.answer?.complete();
    this.answer = null;
  }
}
