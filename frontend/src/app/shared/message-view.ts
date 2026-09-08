import { Component, computed, input, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

import { ISO_CODE_MEANINGS, ISO_LABELS } from '../core/iso20022/iso-labels';

/** One element of the message, ready to render. */
export interface MessageNode {
  label: string;
  tag: string;
  value: string | null;
  meaning: string | null;
  attributes: { name: string; value: string }[];
  depth: number;
  children: MessageNode[];
}

/**
 * A scheme message, readable two ways.
 *
 * The XML is the record and is shown exactly as it was sent or received. The
 * readable view is a rendering of that same document — the tree walked once
 * and every element given its plain English name — not a second source that
 * could drift from it.
 *
 * The mapping is deliberately generic rather than per message type: pacs.008,
 * pacs.002, pacs.004, camt.056 and camt.053 share most of their vocabulary,
 * and an element with no label falls back to its own tag, so a message this
 * bank does not yet send still renders.
 */
@Component({
  selector: 'app-message-view',
  imports: [NgTemplateOutlet],
  templateUrl: './message-view.html',
  styleUrl: './message-view.css',
})
export class MessageView {

  readonly xml = input.required<string>();

  readonly showXml = signal(false);

  /**
   * The parsed document and any failure to read it, derived together.
   *
   * One computation rather than a signal written from inside another: a
   * computed must be pure, and setting an error signal from within one is
   * exactly the write Angular refuses.
   */
  private readonly parsed = computed<{ nodes: MessageNode[]; error: string }>(() => {

    const source = this.xml();

    if (!source) {
      return { nodes: [], error: '' };
    }

    try {
      const document = new DOMParser().parseFromString(source, 'application/xml');

      // A parse failure surfaces as a parsererror element, not an exception.
      if (document.querySelector('parsererror')) {
        return { nodes: [], error: 'This message is not well-formed XML.' };
      }

      const root = document.documentElement;

      return { nodes: root ? [this.toNode(root, 0)] : [], error: '' };

    } catch {
      return { nodes: [], error: 'This message could not be read.' };
    }
  });

  readonly nodes = computed(() => this.parsed().nodes);
  readonly parseError = computed(() => this.parsed().error);

  /**
   * The handful of facts somebody actually wants first.
   *
   * A scheme message is a deep tree and the answer to "what is this?" is
   * usually six values scattered through it. They are pulled out by element
   * name rather than by path, because the same fact sits at a different depth
   * in each definition — the amount is under the group header in one message
   * and under the transaction in another.
   */
  readonly summary = computed<{ label: string; value: string }[]>(() => {

    const flat: MessageNode[] = [];

    const walk = (node: MessageNode) => {
      flat.push(node);
      node.children.forEach(walk);
    };

    this.nodes().forEach(walk);

    const first = (...tags: string[]): MessageNode | undefined =>
      flat.find(n => tags.includes(n.tag) && n.value !== null);

    /*
     * A statement answers different questions from a payment. Amount, payer
     * and beneficiary are single values on a payment and simply do not exist
     * on a statement, so it gets a reading of its own rather than a header
     * with two lines in it.
     */
    const statement = this.nodes()[0]?.children[0];

    if (statement?.tag === 'BkToCstmrStmt') {
      return this.statementFacts(statement);
    }

    const facts: { label: string; value: string }[] = [];

    const add = (label: string, node?: MessageNode, suffix?: string) => {

      if (!node?.value) {
        return;
      }

      const currency = node.attributes.find(a => a.name === 'Ccy')?.value;

      facts.push({
        label,
        value: node.value
          + (currency ? ' ' + currency : '')
          + (suffix ? ' — ' + suffix : ''),
      });
    };

    /*
     * The body element is what the message is: the root is always Document,
     * which says nothing.
     */
    const body = this.nodes()[0]?.children[0];

    if (body) {
      facts.push({ label: 'Message', value: body.label });
    }

    add('Amount', first(
      'IntrBkSttlmAmt', 'RtrdIntrBkSttlmAmt',
      'OrgnlIntrBkSttlmAmt', 'TtlIntrBkSttlmAmt'));

    const status = first('TxSts');

    if (status?.value) {
      facts.push({
        label: 'Status',
        value: status.value + (status.meaning ? ' — ' + status.meaning : ''),
      });
    }

    const reason = flat.find(n => n.tag === 'Cd'
      && ['StsRsnInf', 'RtrRsnInf', 'CxlRsnInf'].some(
        parent => flat.some(p => p.tag === parent
          && p.children.some(c => c.children.includes(n)))));

    if (reason?.value) {
      facts.push({ label: 'Reason', value: reason.value });
    }

    add('Payer', flat.find(n => n.tag === 'Nm'
      && this.isUnder(flat, n, 'Dbtr')));

    add('Beneficiary', flat.find(n => n.tag === 'Nm'
      && this.isUnder(flat, n, 'Cdtr')));

    add('Reference', first('EndToEndId', 'OrgnlEndToEndId'));
    add('Sent', first('CreDtTm'));

    return facts;
  });

  /**
   * What a statement is actually saying: whose account, over what period, and
   * where the balance started and ended.
   */
  private statementFacts(body: MessageNode): { label: string; value: string }[] {

    const stmt = this.descendant(body, 'Stmt') ?? body;
    const facts: { label: string; value: string }[] = [];

    facts.push({ label: 'Message', value: 'Account statement' });

    const account = this.descendant(stmt, 'Acct');
    const iban = this.descendant(account, 'IBAN');

    if (iban?.value) {
      facts.push({ label: 'Account', value: iban.value });
    }

    const owner = this.descendant(this.descendant(stmt, 'Ownr'), 'Nm');

    if (owner?.value) {
      facts.push({ label: 'Held by', value: owner.value });
    }

    const from = this.descendant(stmt, 'FrDtTm');
    const to = this.descendant(stmt, 'ToDtTm');

    if (from?.value && to?.value) {
      facts.push({
        label: 'Period',
        value: `${this.day(from.value)} to ${this.day(to.value)}`,
      });
    }

    /*
     * Balances are told apart by their type code, not by their order, because
     * the schema does not fix which comes first.
     */
    for (const balance of this.descendants(stmt, 'Bal')) {

      const code = this.descendant(balance, 'Cd')?.value;
      const amount = this.descendant(balance, 'Amt');

      if (!amount?.value) {
        continue;
      }

      const label = code === 'OPBD' ? 'Opening balance'
        : code === 'CLBD' ? 'Closing balance'
        : code ?? 'Balance';

      facts.push({ label, value: this.signedMoney(balance, amount) });
    }

    const entries = this.descendant(stmt, 'NbOfNtries');

    if (entries?.value) {
      facts.push({ label: 'Entries', value: entries.value });
    }

    const credits = this.descendant(this.descendant(stmt, 'TtlCdtNtries'), 'Sum');
    const debits = this.descendant(this.descendant(stmt, 'TtlDbtNtries'), 'Sum');

    /*
     * Not "paid in" and "paid out", though they measure the same movements.
     *
     * The statement above nets a round trip away: money that left and came
     * straight back is shown once, in a figure the customer can reconcile
     * against what they know. These are the scheme's own totals and are gross
     * by definition, so the two disagree by exactly the amount that went out
     * and returned. Sharing a label made that look like one of them was
     * wrong; naming them after the fields they come from says which question
     * each is answering.
     */
    if (credits?.value) {
      facts.push({ label: 'Total credits', value: credits.value });
    }

    if (debits?.value) {
      facts.push({ label: 'Total debits', value: debits.value });
    }

    return facts;
  }

  /**
   * A balance carries its sign in a separate indicator, so an overdrawn
   * account is a positive number marked DBIT. Read back, that is a minus.
   */
  private signedMoney(balance: MessageNode, amount: MessageNode): string {

    const currency = amount.attributes.find(a => a.name === 'Ccy')?.value;
    const owed = this.descendant(balance, 'CdtDbtInd')?.value === 'DBIT';

    return (owed ? '-' : '') + amount.value + (currency ? ' ' + currency : '');
  }

  /** The date half of an ISO date-time, which is all a period needs. */
  private day(value: string): string {
    return value.split('T')[0];
  }

  /** The first descendant with the given tag, the node itself included. */
  private descendant(
      node: MessageNode | undefined, tag: string): MessageNode | undefined {

    if (!node) {
      return undefined;
    }

    if (node.tag === tag) {
      return node;
    }

    for (const child of node.children) {

      const found = this.descendant(child, tag);

      if (found) {
        return found;
      }
    }

    return undefined;
  }

  /** Every descendant with the given tag. */
  private descendants(node: MessageNode, tag: string): MessageNode[] {

    const found: MessageNode[] = [];

    const walk = (current: MessageNode) => {

      if (current.tag === tag) {
        found.push(current);
      }

      current.children.forEach(walk);
    };

    walk(node);

    return found;
  }

  /** Whether a node sits somewhere beneath an element with the given tag. */
  private isUnder(
      flat: MessageNode[], node: MessageNode, ancestorTag: string): boolean {

    const parentOf = (child: MessageNode) =>
      flat.find(candidate => candidate.children.includes(child));

    let current: MessageNode | undefined = parentOf(node);

    while (current) {

      if (current.tag === ancestorTag) {
        return true;
      }

      current = parentOf(current);
    }

    return false;
  }

  toggle(): void {
    this.showXml.set(!this.showXml());
  }

  private toNode(element: Element, depth: number): MessageNode {

    const tag = element.localName;

    const children = Array.from(element.children)
      .map(child => this.toNode(child, depth + 1));

    /*
     * Only a leaf carries a value. A parent's textContent would be every
     * descendant's text run together, which reads as nonsense.
     */
    const value = children.length === 0
      ? (element.textContent ?? '').trim() || null
      : null;

    return {
      label: ISO_LABELS[tag] ?? tag,
      tag,
      value,
      meaning: value ? ISO_CODE_MEANINGS[value] ?? null : null,
      attributes: Array.from(element.attributes)
        .filter(attribute => !attribute.name.startsWith('xmlns'))
        .map(attribute => ({ name: attribute.name, value: attribute.value })),
      depth,
      children,
    };
  }
}
