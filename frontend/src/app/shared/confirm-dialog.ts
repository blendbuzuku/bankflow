import { Component, HostListener, inject } from '@angular/core';

import { Confirm } from '../core/services/confirm';

/**
 * The question, and the two ways out of it.
 *
 * Escape and the backdrop both mean no, because the safe answer is the one a
 * stray keypress should give. Only the button says yes.
 */
@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.css',
})
export class ConfirmDialog {

  private readonly service = inject(Confirm);

  readonly request = this.service.request;

  answer(confirmed: boolean): void {
    this.service.respond(confirmed);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {

    if (this.request()) {
      this.answer(false);
    }
  }
}
