package com.bankflow.common.iban;

import java.util.regex.Pattern;

/**
 * The characters ISO 20022 restricted text types actually accept.
 *
 * The scheme's schemas type most free text as restricted FIN text, which
 * permits a narrow Latin set and nothing else. An apostrophe is fine; the
 * curly one a word processor substitutes for it is not, and neither are the
 * accented characters in half the names in Kosovo.
 *
 * Outbound messages we build fold text into this set, because a statement
 * should still be produced for somebody called Behxhet Krasniqi. What a
 * person types into a payment form is different: silently rewriting the
 * beneficiary's name changes who is being paid, so here it is refused and
 * said out loud instead.
 */
public final class SchemeText {

    private static final Pattern PERMITTED =
            Pattern.compile("[0-9a-zA-Z/\\-?:().,'+ ]*");

    private SchemeText() {
    }

    /**
     * Why the scheme would refuse this text, or null if it would not.
     *
     * @param label how to name the field to somebody who has to fix it
     * @param max   the ISO maximum for this element, in characters
     */
    public static String reasonInvalid(String value, String label, int max) {

        if (value == null || value.isEmpty()) {
            return null;
        }

        if (value.length() > max) {
            return "%s must be %d characters or fewer; this is %d".formatted(
                    label, max, value.length()
            );
        }

        if (!PERMITTED.matcher(value).matches()) {
            return "%s contains characters the scheme will not carry: %s. "
                    .formatted(label, offending(value))
                    + "Letters a-z, digits, and / - ? : ( ) . , ' + are allowed";
        }

        return null;
    }

    /**
     * The specific characters at fault.
     *
     * Naming them is the difference between an error somebody can fix and one
     * they retype the whole field to escape -- an accented letter or a curly
     * quote is not visibly wrong at a glance.
     */
    private static String offending(String value) {

        StringBuilder found = new StringBuilder();

        value.codePoints().forEach(point -> {

            String character = new String(Character.toChars(point));

            if (!PERMITTED.matcher(character).matches()
                    && found.indexOf(character) < 0) {

                found.append(character);
            }
        });

        return found.toString();
    }
}
