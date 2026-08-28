package com.bankflow.common.audit;

/**
 * Business events worth answering to a regulator for.
 *
 * These describe what happened in banking terms, not what changed in a table.
 *
 * Shared rather than owned by one service because the audit trail is one
 * table. A teller approving a client and a payment settling belong on the same
 * trail — asked "who let this person bank with us, and what did they then do",
 * nobody wants two answers from two places.
 */
public enum AuditEventType {

    /*
     * Deciding to pay is not paying. Everything down to PAYMENT_DECLINED
     * records an intention, a check, or a permission — the money is still
     * where it was.
     */
    PAYMENT_INITIATED(false),
    PAYMENT_VALIDATED(false),
    PAYMENT_SUBMITTED_FOR_APPROVAL(false),
    PAYMENT_APPROVED(false),
    PAYMENT_DECLINED(false),

    /** Pricing a payment moves nothing; collecting the price does. */
    FEE_ASSESSED(false),
    FEE_CHARGED(true),

    ACCOUNT_DEBITED(true),
    ACCOUNT_CREDITED(true),
    LEDGER_ENTRY_POSTED(true),

    /*
     * Sending is financial: the debtor is debited and suspense credited before
     * the message leaves. Failing is not — it records that nothing happened.
     */
    PAYMENT_SENT(true),
    PAYMENT_SETTLED(true),
    PAYMENT_REJECTED(true),
    PAYMENT_RETURNED(true),
    PAYMENT_FAILED(false),
    PAYMENT_REVERSED(true),

    /**
     * A recall was asked for, agreed to, refused, or arrived from a peer.
     *
     * None of them move money, including acceptance: agreeing to send a
     * payment back is a promise, and the return that keeps it is booked as its
     * own event. This is the distinction the flag exists to make — a recall
     * queue full of activity against a ledger that has not moved.
     */
    RECALL_REQUESTED(false),
    RECALL_RECEIVED(false),
    RECALL_ACCEPTED(false),
    RECALL_REJECTED(false),

    PACS_MESSAGE_GENERATED(false),
    PACS_MESSAGE_RECEIVED(false),

    /** A day was signed off, or an attempt to sign it off was refused. */
    DAY_CLOSED(false),
    DAY_CLOSE_REFUSED(false),

    /** The list of banks that can be paid was changed. */
    BANK_DIRECTORY_CHANGED(false),

    TARIFF_RULE_CREATED(false),
    TARIFF_RULE_CHANGED(false),

    /*
     * Who was let in, and by whom.
     *
     * A client being approved is the moment somebody at this bank vouched that
     * the person is who they say they are. It moves no money and every
     * subsequent payment they make rests on it, which is exactly why it needs
     * to be on the trail rather than inferred from a status column that only
     * ever shows its current value.
     */
    CLIENT_REGISTERED(false),
    CLIENT_APPROVED(false),
    CLIENT_SUSPENDED(false),

    ACCOUNT_OPENED(false),
    ACCOUNT_CLOSED(false),

    /**
     * Who may act for a client, and who said so.
     *
     * A signatory can instruct payments on somebody else's account, which
     * makes granting that authority one of the more consequential things a
     * bank does without any money moving.
     */
    SIGNATORY_ADDED(false),
    SIGNATORY_REMOVED(false);

    private final boolean financial;

    AuditEventType(boolean financial) {
        this.financial = financial;
    }

    /**
     * Whether this event is money actually moving on the books.
     *
     * The trail mixes two kinds of thing that read alike and answer to
     * different people. A reviewer asking "what did this account do" wants the
     * financial events and nothing else; somebody asking "who approved this,
     * and on whose authority" wants the rest. Deriving it from the event type
     * rather than storing a caller's opinion means the two can never disagree.
     */
    public boolean isFinancial() {
        return financial;
    }
}
