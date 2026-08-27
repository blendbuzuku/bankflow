import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Toasts } from '../../core/services/toasts';
import {
  MessageSummary,
  MessageTypeOption,
  TransactionService,
} from '../../core/services/transaction';
import { AccountResponse, AccountService } from '../../core/services/account';
import { MessageView } from '../../shared/message-view';
import { HasUnsavedChanges } from '../../core/guards/unsaved-changes';

/**
 * Every scheme message, readable.
 *
 * The per-payment view answers "what happened to this payment". This answers
 * the question somebody actually starts from when a scheme problem lands on
 * their desk — "what has been going in and out" — before they know which
 * payment is at fault.
 *
 * It also takes pasted XML, so a message a counterparty sends by mail can be
 * read here without first getting it into the system.
 */
@Component({
  selector: 'app-message-inspector',
  imports: [DatePipe, FormsModule, RouterLink, MessageView],
  templateUrl: './message-inspector.html',
  styleUrl: './message-inspector.css',
})
export class MessageInspector implements OnInit, HasUnsavedChanges {

  /** Pasted XML is typed-in work; a chosen message is not. */
  hasUnsavedChanges(): boolean {
    return this.pasteMode() && !!this.pasted.trim();
  }


  private readonly transactionService = inject(TransactionService);
  private readonly accountService = inject(AccountService);
  private readonly toasts = inject(Toasts);

  readonly messages = signal<MessageSummary[]>([]);
  readonly loading = signal(true);

  readonly selected = signal<MessageSummary | null>(null);
  readonly xml = signal('');
  readonly loadingXml = signal(false);

  /** Pasted XML, which needs no stored message behind it. */
  readonly pasteMode = signal(false);
  pasted = '';

  /** What was searched for, so the empty state can say so. */
  readonly lastSearch = signal('');

  readonly types = signal<MessageTypeOption[]>([]);
  readonly accounts = signal<AccountResponse[]>([]);

  query = '';
  typeFilter = '';
  directionFilter = '';

  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {

    this.load();

    this.transactionService.messageTypes().subscribe({
      next: types => this.types.set(types),
      error: () => this.types.set([]),
    });

    /*
     * The account list is the shortcut that makes this usable: almost nobody
     * knows an IBAN by heart, but everyone can recognise one in a list.
     */
    this.accountService.getAllAccounts().subscribe({
      next: accounts => this.accounts.set(
        accounts.filter(a => a.accountType === 'CURRENT'
          || a.accountType === 'SAVINGS')),
      error: () => this.accounts.set([]),
    });
  }

  load(): void {

    this.loading.set(true);
    this.lastSearch.set(this.query.trim());

    this.transactionService.searchMessages({
      q: this.query,
      type: this.typeFilter || undefined,
      direction: this.directionFilter || undefined,
    }).subscribe({
      next: messages => {
        this.messages.set(messages);
        this.loading.set(false);

        if (messages.length && !this.pasteMode()) {
          this.open(messages[0]);
        } else if (!messages.length) {
          this.selected.set(null);
          this.xml.set('');
        }
      },
      error: error => {
        this.loading.set(false);
        this.toasts.failure('The search failed', error);
      },
    });
  }

  /** Waits for the typing to stop rather than searching on every keystroke. */
  onQueryChanged(): void {

    if (this.timer) {
      clearTimeout(this.timer);
    }

    this.timer = setTimeout(() => this.load(), 300);
  }

  /** Searching by an account means searching by its IBAN. */
  searchAccount(iban: string): void {

    if (!iban) {
      return;
    }

    this.query = iban;
    this.load();
  }

  chooseType(name: string): void {
    this.typeFilter = this.typeFilter === name ? '' : name;
    this.load();
  }

  chooseDirection(direction: string): void {
    this.directionFilter = this.directionFilter === direction ? '' : direction;
    this.load();
  }

  clearAll(): void {
    this.query = '';
    this.typeFilter = '';
    this.directionFilter = '';
    this.load();
  }

  hasFilters(): boolean {
    return !!(this.query.trim() || this.typeFilter || this.directionFilter);
  }

  visible(): MessageSummary[] {
    return this.messages();
  }

  open(message: MessageSummary): void {

    this.pasteMode.set(false);
    this.selected.set(message);
    this.xml.set('');
    this.loadingXml.set(true);

    this.transactionService.messageXml(message.messageId).subscribe({
      next: xml => {
        this.xml.set(xml);
        this.loadingXml.set(false);
      },
      error: error => {
        this.loadingXml.set(false);
        this.toasts.failure('That message could not be loaded', error);
      },
    });
  }

  startPaste(): void {
    this.pasteMode.set(true);
    this.selected.set(null);
    this.xml.set('');
    this.pasted = '';
  }

  readPasted(): void {

    if (!this.pasted.trim()) {
      return;
    }

    this.xml.set(this.pasted.trim());
  }

  clearPasted(): void {
    this.pasted = '';
    this.xml.set('');
  }

  tone(message: MessageSummary): string {

    if (message.reasonCode) {
      return 'bad';
    }

    return message.direction === 'INBOUND' ? 'in' : 'out';
  }
}
