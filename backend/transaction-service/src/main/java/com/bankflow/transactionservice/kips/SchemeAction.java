package com.bankflow.transactionservice.kips;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment the scheme still has something to say about.
 *
 * Deliberately shaped from the counterparty's side rather than ours: it names
 * the creditor and their bank, because that is who would be acting, and it
 * carries the rail, because a return on ACH is a different message version to
 * a return on RTGS.
 */
public record SchemeAction(

        String transactionReference,
        String endToEndId,
        BigDecimal amount,
        String currency,

        /** ACH or RTGS — the dialect any answer has to be written in. */
        String rail,

        String creditorName,
        String creditorIban,
        String creditorAgentBic,

        LocalDateTime sentAt,

        /**
         * Set when we have asked for this payment back and are waiting.
         *
         * The scheme is under no obligation to agree, so this is context for
         * whoever is playing the counterparty, not an instruction to them.
         */
        String recallId,
        String recallReason
) {
}
