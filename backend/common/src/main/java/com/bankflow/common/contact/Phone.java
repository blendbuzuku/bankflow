package com.bankflow.common.contact;

import java.util.Map;
import java.util.Set;

/**
 * Checks a telephone number against the country that issued it.
 *
 * "Thirty characters or fewer" is not a phone number check. A bank telephones
 * customers about payments it has stopped, and a number that cannot be dialled
 * is discovered at exactly the moment somebody needed to be reached.
 *
 * Numbers are held in E.164 -- plus, country code, national number, no spaces
 * or punctuation -- because that is the only form that is unambiguous once the
 * customer is no longer in the country they signed up in. A Kosovo number
 * written 044 123 456 means nothing to a switch in Germany; +38344123456 means
 * the same thing everywhere.
 */
public final class Phone {

    /**
     * National number lengths per calling code, excluding the code itself.
     *
     * The region this bank serves, plus where the diaspora it serves lives:
     * a Kosovo bank's customers are as likely to be reachable on a Swiss or
     * German number as a local one.
     */
    private static final Map<String, Set<Integer>> NATIONAL_LENGTHS = Map.ofEntries(
            Map.entry("383", Set.of(8, 9)),      // Kosovo
            Map.entry("355", Set.of(9)),          // Albania
            Map.entry("389", Set.of(8)),          // North Macedonia
            Map.entry("381", Set.of(8, 9)),       // Serbia
            Map.entry("382", Set.of(8)),          // Montenegro
            Map.entry("387", Set.of(8)),          // Bosnia and Herzegovina
            Map.entry("385", Set.of(8, 9)),       // Croatia
            Map.entry("386", Set.of(8)),          // Slovenia
            Map.entry("30", Set.of(10)),          // Greece
            Map.entry("39", Set.of(9, 10, 11)),   // Italy
            Map.entry("41", Set.of(9)),           // Switzerland
            Map.entry("43", Set.of(10, 11, 12, 13)), // Austria
            Map.entry("44", Set.of(10)),          // United Kingdom
            Map.entry("45", Set.of(8)),           // Denmark
            Map.entry("46", Set.of(7, 8, 9)),     // Sweden
            Map.entry("47", Set.of(8)),           // Norway
            Map.entry("49", Set.of(10, 11)),      // Germany
            Map.entry("31", Set.of(9)),           // Netherlands
            Map.entry("32", Set.of(8, 9)),        // Belgium
            Map.entry("33", Set.of(9)),           // France
            Map.entry("90", Set.of(10)),          // Turkey
            Map.entry("1", Set.of(10))            // US and Canada
    );

    /** Longest calling code in the table, so the lookup knows when to stop. */
    private static final int MAX_CODE_LENGTH = 3;

    /** E.164 caps the whole number, country code included, at fifteen digits. */
    private static final int E164_MAX_DIGITS = 15;

    private Phone() {
    }

    /**
     * Strips how people write numbers down to how they are dialled.
     *
     * Spaces, brackets, dashes and dots are presentation. A leading 00 is the
     * old international prefix and means the same as a plus.
     */
    public static String normalise(String phone) {

        if (phone == null) {
            return null;
        }

        String digits = phone.replaceAll("[\\s().\\-/]", "").trim();

        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }

        return digits;
    }

    /**
     * Why this number could not be dialled, or null if it could.
     *
     * A blank number is not an error here: whether a phone number is required
     * is the caller's decision, and for a bank the answer differs between a
     * customer and a prospect.
     */
    public static String reasonInvalid(String raw) {

        String phone = normalise(raw);

        if (phone == null || phone.isEmpty()) {
            return null;
        }

        if (!phone.startsWith("+")) {
            return "Start the number with the country code, like +383 44 123 456. "
                    + "Without it the number cannot be dialled from abroad";
        }

        String digits = phone.substring(1);

        if (!digits.matches("\\d+")) {
            return "A telephone number is digits only after the country code";
        }

        if (digits.length() > E164_MAX_DIGITS) {
            return "A telephone number is at most %d digits including the "
                    .formatted(E164_MAX_DIGITS)
                    + "country code; this one has " + digits.length();
        }

        String code = callingCode(digits);

        if (code == null) {
            return "%s is not a country calling code this bank recognises"
                    .formatted("+" + digits.substring(
                            0, Math.min(MAX_CODE_LENGTH, digits.length())));
        }

        String national = digits.substring(code.length());
        Set<Integer> expected = NATIONAL_LENGTHS.get(code);

        if (!expected.contains(national.length())) {

            return "A +%s number has %s digits after the country code; this one has %d"
                    .formatted(code, describe(expected), national.length());
        }

        return null;
    }

    public static boolean isValid(String raw) {
        return reasonInvalid(raw) == null;
    }

    /**
     * Longest match wins.
     *
     * Calling codes are a prefix code by design, but only if you match greedily:
     * 1 and 383 both start a valid number and the shorter would win otherwise.
     */
    private static String callingCode(String digits) {

        for (int length = MAX_CODE_LENGTH; length >= 1; length--) {

            if (digits.length() < length) {
                continue;
            }

            String candidate = digits.substring(0, length);

            if (NATIONAL_LENGTHS.containsKey(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    /** "8 or 9" reads better than a set literal to somebody fixing a form. */
    private static String describe(Set<Integer> lengths) {

        return lengths.stream()
                .sorted()
                .map(String::valueOf)
                .reduce((a, b) -> a + " or " + b)
                .orElse("");
    }
}
