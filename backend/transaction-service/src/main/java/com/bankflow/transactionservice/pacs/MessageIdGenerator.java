package com.bankflow.transactionservice.pacs;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Builds message identifiers in the shape KIPS participants use.
 *
 * Observed format, e.g. {@code MBKO202505229076374519}: a four-character
 * institution prefix, the date, then a numeric sequence — 22 characters, well
 * inside the 35 the schema allows.
 *
 * The character set matters. {@code BizMsgIdr} is restricted to
 * {@code [0-9a-zA-Z/-?:().,'+ ]}, which notably excludes underscores, so
 * identifiers are built from alphanumerics only rather than from anything
 * resembling a Java constant name.
 */
@Component
public class MessageIdGenerator {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final int SEQUENCE_DIGITS = 10;

    private final SecureRandom random = new SecureRandom();
    private final KipsProperties properties;

    public MessageIdGenerator(KipsProperties properties) {
        this.properties = properties;
    }

    /**
     * A fresh identifier. Random rather than a counter because a counter would
     * need coordination across instances to stay unique, and the database's
     * unique constraint is the real guard either way.
     */
    public String newMessageId() {

        StringBuilder sequence = new StringBuilder(SEQUENCE_DIGITS);

        for (int i = 0; i < SEQUENCE_DIGITS; i++) {
            sequence.append(random.nextInt(10));
        }

        return properties.institutionPrefix()
                + LocalDate.now().format(DATE)
                + sequence;
    }

    /**
     * A UETR — the UUIDv4 that follows an RTGS payment end to end. Distinct
     * from our own message identifier: the UETR stays constant across every
     * message about the payment, including a counterparty's status report.
     */
    public String newUetr() {
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Whether a value is acceptable as a BizMsgIdr.
     */
    public static boolean isValidBusinessMessageId(String value) {

        return value != null
                && !value.isEmpty()
                && value.length() <= 35
                && value.matches("[0-9a-zA-Z/\\-?:().,'+ ]+");
    }
}
