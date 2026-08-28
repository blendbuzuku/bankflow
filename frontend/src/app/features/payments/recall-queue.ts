import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth';
import { Toasts } from '../../core/services/toasts';
import { WorkQueues } from '../../core/services/work-queues';
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

  accept(recall: RecallResponse): void {

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
