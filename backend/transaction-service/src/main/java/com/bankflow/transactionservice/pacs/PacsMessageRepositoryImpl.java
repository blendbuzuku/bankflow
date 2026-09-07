package com.bankflow.transactionservice.pacs;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The message search, assembled rather than declared.
 *
 * Named for the repository it completes -- Spring Data looks for an Impl
 * suffix and wires it in, so callers still see one repository.
 */
public class PacsMessageRepositoryImpl implements PacsMessageSearch {

    /**
     * Where the searchable facts actually live.
     *
     * An IBAN, a name or an amount is inside the XML rather than in a field of
     * its own, so the body is searched alongside the identifiers. That is a
     * scan, deliberately: extracting those into fields would mean re-parsing
     * every message ever stored to backfill, and an honest scan beats a
     * half-populated field that quietly misses older traffic.
     */
    private static final String[] SEARCHABLE = {
            "messageId",
            "transactionReference",
            "endToEndId",
            "statusCode",
            "reasonCode",
            "rawXml"
    };

    private final MongoTemplate mongo;

    public PacsMessageRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<PacsMessage> search(
            String term,
            PacsMessageType type,
            MessageDirection direction,
            Pageable page) {

        List<Criteria> conditions = new ArrayList<>();

        if (term != null && !term.isBlank()) {

            /*
             * Quoted, so a search for "(" is a search for a bracket rather than
             * an unterminated group the regex engine rejects. People paste
             * references and names in here, and both contain punctuation.
             */
            String quoted = Pattern.quote(term.trim());

            Criteria[] anyField = new Criteria[SEARCHABLE.length];

            for (int i = 0; i < SEARCHABLE.length; i++) {
                anyField[i] = Criteria.where(SEARCHABLE[i]).regex(quoted, "i");
            }

            conditions.add(new Criteria().orOperator(anyField));
        }

        if (type != null) {
            conditions.add(Criteria.where("messageType").is(type));
        }

        if (direction != null) {
            conditions.add(Criteria.where("direction").is(direction));
        }

        Query query = conditions.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(conditions));

        /*
         * Newest first, because somebody opening the inspector is nearly always
         * looking at what just happened. The page bounds how much comes back;
         * the caller decides how much that is.
         */
        query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        query.with(page);

        return mongo.find(query, PacsMessage.class);
    }
}
