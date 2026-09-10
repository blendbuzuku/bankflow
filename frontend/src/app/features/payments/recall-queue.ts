import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth';
import { Toasts } from '../../core/services/toasts';
import { WorkQueues } from '../../core/services/work-queues';
import { Confirm } from '../../core/services/confirm';
import {
  RecallResponse,
  TransactionService,
} from '../../core/services/transaction';

/**
 * Recalls waiting for an answer.
 *
 * Two kinds sit here and they are not the same thing. An inbound request is
 * another bank asking us to send money back, and it is ours to decide. An
 * outbound one is us asking them, and there is nothing to decide — it is shown
 * so the person who raised it can see it is still unanswered.
 */
@Component({
  selector: 'app-recall-queue',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './recall-queue.html',
  styleUrl: './recall-queue.css',
})
export class RecallQueue implements OnInit {

  private readonly transactionService = inject(TransactionService);
  private readonly authService = inject(AuthService);
  private readonly toasts = inject(Toasts);
  private readonly queues = inject(WorkQueues);
  private readonly confirm = inject(Confirm);

  readonly recalls = signal<RecallResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');

  readonly busy = signal<number | null>(null);
  readonly deciding = signal<number | null>(null);

  decisionNote = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {

    this.loading.set(true);
    this.error.set('');

    this.transactionService.openRecalls().subscribe({
      next: recalls => {
        this.recalls.set(recalls);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the recall queue.');
        this.toasts.error('Could not load the recall queue');
        this.loading.set(false);
      },
    });
  }

  /** Only a request from another bank is ours to answer. */
  isOurs(recall: RecallResponse): boolean {
    return recall.direction === 'INBOUND';
  }

  canDecide(): boolean {
    return this.authService.canApprove();
  }

  startDecision(recall: RecallResponse): void {
    this.deciding.set(recall.id);
    this.decisionNote = '';
  }

  cancelDecision(): void {
    this.deciding.set(null);
    this.decisionNote = '';
  }

  /** What the decision is about, laid out the same way for either answer. */
  private recallFacts(recall: RecallResponse) {

    const facts = [
      { label: 'Payment', value: recall.transactionReference },
      { label: 'Their reason', value: `${recall.reasonCode} — ${recall.reasonDescription}` },
      { label: 'Their reference', value: recall.cancellationId },
    ];

    if (this.decisionNote.trim()) {
      facts.push({ label: 'Your note', value: this.decisionNote.trim() });
    }

    return facts;
  }

  /**
   * Accepting takes money out of our customer's account and sends it back to
   * the other bank. Refusing and accepting sit side by side, and a slip
   * between them is exactly what a second look is for.
   */
  accept(recall: RecallResponse): void {

    this.confirm.askThen({
      title: 'Accept this recall?',
      message: 'The funds are taken back from our customer and returned to the '
        + 'sending bank with a pacs.004.',
      facts: this.recallFacts(recall),
      note: 'This cannot be undone. The money leaves the customer\'s account '
        + 'now, whether or not they still hold it.',
      confirmLabel: 'Accept and return the funds',
      cancelLabel: 'Go back',
      danger: true,
    }, () => this.doAccept(recall));
  }

  private doAccept(recall: RecallResponse): void {

    this.busy.set(recall.id);
    this.error.set('');

    this.transactionService.acceptRecall(recall.id, this.decisionNote).subscribe({
      next: () => {
        this.busy.set(null);
        this.deciding.set(null);
        this.decisionNote = '';
        this.toasts.success(
          'Recall accepted',
          `${recall.transactionReference} was returned to the sending bank.`,
        );
        this.load();

        /*
         * The badge counts this queue, and acting on it does not
         * navigate -- so without this the shell keeps claiming the
         * item is still waiting until the page is reloaded.
         */
        this.queues.refresh();
      },
      error: error => {
        this.busy.set(null);
        this.toasts.failure('The recall could not be accepted', error);
      },
    });
  }

  reject(recall: RecallResponse): void {

    this.confirm.askThen({
      title: 'Refuse this recall?',
      message: 'The payment stands and the customer keeps the money. The '
        + 'sending bank is told the answer is no.',
      facts: this.recallFacts(recall),
      note: 'This is the final answer to this request. The other bank would '
        + 'have to raise a new one to ask again.',
      confirmLabel: 'Refuse the recall',
      cancelLabel: 'Go back',
    }, () => this.doReject(recall));
  }

  private doReject(recall: RecallResponse): void {

    this.busy.set(recall.id);
    this.error.set('');

    this.transactionService.rejectRecall(recall.id, this.decisionNote).subscribe({
      next: () => {
        this.busy.set(null);
        this.deciding.set(null);
        this.decisionNote = '';
        this.toasts.info(
          'Recall refused',
          `${recall.transactionReference} stands. No funds moved.`,
        );
        this.load();

        /*
         * The badge counts this queue, and acting on it does not
         * navigate -- so without this the shell keeps claiming the
         * item is still waiting until the page is reloaded.
         */
        this.queues.refresh();
      },
      error: error => {
        this.busy.set(null);
        this.toasts.failure('The recall could not be refused', error);
      },
    });
  }
}
