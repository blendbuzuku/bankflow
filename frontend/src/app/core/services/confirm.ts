import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/** One line of what is about to happen: "Amount — 200.00 EUR". */
export interface ConfirmFact {
  label: string;
  value: string;
}

export interface ConfirmRequest {
  title: string;
  message: string;

  /**
   * The specifics, laid out to be checked rather than read.
   *
   * A sentence saying "send this payment?" asks for trust. The amount, the
   * beneficiary and the charge laid out underneath let the person see the
   * thing they are agreeing to — which is the only reason to stop and ask.
   */
  facts: ConfirmFact[];

  /** What cannot be taken back, said once and set apart from the rest. */
  note: string;

  confirmLabel: string;

  /** Empty when there is nothing to decide — the dialog is only telling. */
  cancelLabel: string;

  /** Marks the confirming action as destructive, so it reads as one. */
  danger: boolean;
}

type AskRequest = Partial<ConfirmRequest> & { message: string };

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

  ask(request: AskRequest): Observable<boolean> {

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
      facts: request.facts ?? [],
      note: request.note ?? '',
      confirmLabel: request.confirmLabel ?? 'Continue',
      cancelLabel: request.cancelLabel ?? 'Cancel',
      danger: request.danger ?? false,
    });

    return this.answer.asObservable();
  }

  /**
   * Asks, and does the thing only if the answer is yes.
   *
   * Most callers want exactly this and nothing else: a no is not an event,
   * it is the absence of one.
   */
  askThen(request: AskRequest, action: () => void): void {

    this.ask(request).subscribe(confirmed => {

      if (confirmed) {
        action();
      }
    });
  }

  /**
   * Tells the user something that matters too much for a toast, and waits
   * until they have seen it.
   *
   * For the outcome of an action somebody has to act on next — a toast fades
   * on its own schedule, and the person may have looked away.
   */
  inform(request: AskRequest): Observable<boolean> {

    return this.ask({
      ...request,
      title: request.title ?? 'Done',
      confirmLabel: request.confirmLabel ?? 'OK',
      cancelLabel: '',
    });
  }

  respond(confirmed: boolean): void {

    this.request.set(null);

    this.answer?.next(confirmed);
    this.answer?.complete();
    this.answer = null;
  }
}
