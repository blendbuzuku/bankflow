import {
  Component,
  ElementRef,
  HostListener,
  effect,
  inject,
  viewChild,
} from '@angular/core';

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

  /** Cancel when there is a choice; the only button when there is not. */
  private readonly safe = viewChild<ElementRef<HTMLButtonElement>>('safe');

  constructor() {

    /*
     * Focus lands on the safe answer, not the consequential one.
     *
     * Whatever had focus before — often the very button that opened the
     * dialog — would otherwise keep it underneath, so Enter would press a
     * control the person can no longer see, and a keyboard user would have
     * to tab through the page to reach the question at all.
     */
    effect(() => {

      const button = this.safe();

      if (this.request() && button) {
        button.nativeElement.focus();
      }
    });
  }

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
