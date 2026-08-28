/**
 * The same IBAN and scheme-text rules the server enforces.
 *
 * Duplicated deliberately rather than asked for over the wire: the point is to
 * say so while the field still has the cursor in it. The server remains the
 * authority and repeats every check — this only saves the round trip needed to
 * be told what the form already knows.
 */

/** Complete IBAN length per country, from the SWIFT IBAN registry. */
export const IBAN_LENGTHS: Record<string, number> = {
  XK: 20, AL: 28, AT: 20, BA: 20, BE: 16, BG: 22, CH: 21, CY: 28, CZ: 24,
  DE: 22, DK: 18, EE: 20, ES: 24, FI: 18, FR: 27, GB: 22, GR: 27, HR: 21,
  HU: 28, IE: 22, IT: 27, LT: 20, LU: 20, LV: 21, ME: 22, MK: 19, MT: 31,
  NL: 18, NO: 15, PL: 28, PT: 25, RO: 24, RS: 22, SE: 24, SI: 19, SK: 24,
  SM: 27, TR: 26,
};

/** IBANs are printed in groups of four, so pasted ones arrive with spaces. */
export function normaliseIban(value: string | null | undefined): string {
  return (value ?? '').replace(/[\s ]/g, '').toUpperCase();
}

/**
 * ISO 7064 MOD-97-10. A valid IBAN leaves a remainder of 1.
 *
 * The number is far too large for a JS number, so the remainder is carried
 * along digit by digit instead of being computed in one go.
 */
function mod97(iban: string): number {

  const rearranged = iban.slice(4) + iban.slice(0, 4);

  let remainder = 0;

  for (const character of rearranged) {

    if (character >= '0' && character <= '9') {
      remainder = (remainder * 10 + (character.charCodeAt(0) - 48)) % 97;
    } else {
      remainder = (remainder * 100 + (character.charCodeAt(0) - 55)) % 97;
    }
  }

  return remainder;
}

/** Why this is not an IBAN, or null if it is one. */
export function ibanProblem(value: string | null | undefined): string | null {

  const iban = normaliseIban(value);

  if (!iban) {
    return null;
  }

  if (!/^[A-Z]{2}[0-9]{2}[A-Z0-9]+$/.test(iban)) {
    return 'An IBAN starts with two letters for the country and two check '
      + 'digits, then the account number.';
  }

  if (iban.length < 15 || iban.length > 34) {
    return `An IBAN is between 15 and 34 characters; this one is ${iban.length}.`;
  }

  const country = iban.slice(0, 2);
  const expected = IBAN_LENGTHS[country];

  if (expected && iban.length !== expected) {
    return `A ${country} IBAN is ${expected} characters; this one is ${iban.length}.`;
  }

  if (mod97(iban) !== 1) {
    return 'The check digits do not match the rest of the IBAN, so something '
      + 'in it has been mistyped.';
  }

  return null;
}

/** KIPS clears Kosovo only, so a foreign IBAN cannot go on a domestic rail. */
export function wrongCountryForRail(
  value: string | null | undefined,
  rail: string,
): string | null {

  const iban = normaliseIban(value);

  if (!iban || iban.length < 2) {
    return null;
  }

  const domestic = rail === 'KIPS_ACH' || rail === 'KIPS_RTGS';

  if (domestic && !iban.startsWith('XK')) {
    return 'KIPS clears Kosovo payments only. Send this as an international '
      + 'payment instead.';
  }

  return null;
}

const BIC = /^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$/;

export function bicProblem(value: string | null | undefined): string | null {

  const bic = (value ?? '').trim().toUpperCase();

  if (!bic) {
    return null;
  }

  return BIC.test(bic)
    ? null
    : 'A BIC is 8 or 11 characters: four for the bank, two for the country, '
      + 'two for the location, and optionally three for a branch.';
}

/**
 * The characters ISO 20022 restricted text will carry.
 *
 * Not folded silently here the way an outgoing statement folds them: changing
 * the beneficiary's name changes who is being paid, so it is refused and said
 * out loud instead.
 */
const PERMITTED = /^[0-9a-zA-Z/\-?:().,'+ ]*$/;

export function schemeTextProblem(
  value: string | null | undefined,
  label: string,
  max: number,
): string | null {

  const text = value ?? '';

  if (!text) {
    return null;
  }

  if (text.length > max) {
    return `${label} must be ${max} characters or fewer; this is ${text.length}.`;
  }

  if (!PERMITTED.test(text)) {

    const offending = [...new Set(
      [...text].filter(character => !PERMITTED.test(character)),
    )].join('');

    return `${label} contains characters the scheme will not carry: ${offending}. `
      + `Letters a-z, digits, and / - ? : ( ) . , ' + are allowed.`;
  }

  return null;
}

/** Money has two decimal places; the ledger stores no more than that. */
export function amountProblem(value: number | null): string | null {

  if (value === null || value === undefined) {
    return null;
  }

  if (value <= 0) {
    return 'The amount must be greater than zero.';
  }

  const decimals = (String(value).split('.')[1] ?? '').length;

  return decimals > 2
    ? 'The amount cannot be more precise than a cent.'
    : null;
}
