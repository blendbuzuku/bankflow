import { Component, inject } from '@angular/core';

import { Toasts } from '../core/services/toasts';

/**
 * Where confirmations and failures appear.
 *
 * Lives in the shell so it is present on every screen including sign-in, which
 * sits outside the application chrome and is where the first thing a user can
 * get wrong actually happens.
 */
@Component({
  selector: 'app-toast-stack',
  templateUrl: './toast-stack.html',
  styleUrl: './toast-stack.css',
})
export class ToastStack {

  private readonly service = inject(Toasts);

  readonly toasts = this.service.toasts;

  dismiss(id: number): void {
    this.service.dismiss(id);
  }

  /**
   * A failure is announced assertively so a screen reader interrupts with it;
   * a confirmation is announced politely so it waits its turn.
   */
  liveRole(kind: string): 'alert' | 'status' {
    return kind === 'error' ? 'alert' : 'status';
  }
}
