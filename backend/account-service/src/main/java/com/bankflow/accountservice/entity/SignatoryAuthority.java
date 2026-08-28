package com.bankflow.accountservice.entity;

/**
 * How far somebody acting for a client may go.
 *
 * Two levels rather than a permission matrix, because the line that actually
 * matters at a bank is whether you can move money. A bookkeeper who reconciles
 * a company's statements needs to see everything and must not be able to
 * instruct a payment; blurring those two is how a single compromised login
 * empties an account.
 */
public enum SignatoryAuthority {

    /** May instruct payments and see everything. */
    SIGNATORY,

    /** May see the accounts and their history, and move nothing. */
    VIEWER
}
