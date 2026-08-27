package com.bankflow.transactionservice.recall;

/**
 * How far a recall has got.
 *
 * A recall is a request, not an instruction. Asking moves no money — only the
 * answer does, and the answer may be no.
 */
public enum RecallStatus {

    /** Asked for, awaiting an answer. */
    REQUESTED,

    /** Agreed to. The funds went back, carried by a pacs.004. */
    ACCEPTED,

    /** Refused. The original payment stands. */
    REJECTED
}
