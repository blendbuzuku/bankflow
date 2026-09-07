package com.bankflow.transactionservice.pacs;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Finding messages by whatever the person happens to know.
 *
 * Separated from the repository interface because the filters are optional and
 * independent, which a single stored query cannot express. A Mongo query is a
 * document built in full before it is sent, so a placeholder for an absent term
 * still has to hold something -- and a regex holding null is rejected by the
 * driver before any of the surrounding conditions are even considered. That is
 * exactly the trap the first version fell into, having been translated from
 * JPQL where an IS NULL guard short-circuits.
 *
 * Built condition by condition instead, so an absent filter contributes
 * nothing at all rather than contributing something that has to be neutralised.
 */
public interface PacsMessageSearch {

    /**
     * @param term      free text; an IBAN, a name, an amount. Matched against
     *                  the identifiers and against the message body, since the
     *                  facts people search by live inside the XML. Null or
     *                  blank matches everything, which is what an empty search
     *                  box should do.
     * @param type      restrict to one message definition, or null for all
     * @param direction restrict to what we sent or received, or null for both
     */
    List<PacsMessage> search(
            String term,
            PacsMessageType type,
            MessageDirection direction,
            Pageable page
    );
}
