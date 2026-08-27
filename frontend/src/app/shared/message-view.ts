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
