/**
 * The client-record rules the server enforces, repeated at the keyboard.
 *
 * Onboarding is a form somebody fills in once, in front of a teller or on
 * their own phone. Being told after submitting that the date of birth was
 * wrong means starting a conversation again; being told while the field has
 * focus means fixing a digit.
 */

/** National number lengths per calling code, excluding the code. */
const NATIONAL_LENGTHS: Record<string, number[]> = {
  '383': [8, 9], '355': [9], '389': [8], '381': [8, 9], '382': [8],
  '387': [8], '385': [8, 9], '386': [8], '30': [10], '39': [9, 10, 11],
  '41': [9], '43': [10, 11, 12, 13], '44': [10], '45': [8], '46': [7, 8, 9],
  '47': [8], '49': [10, 11], '31': [9], '32': [8, 9], '33': [9],
  '90': [10], '1': [10],
};

/** Spaces, brackets and dashes are presentation; 00 is an old +. */
export function normalisePhone(value: string | null | undefined): string {

  let digits = (value ?? '').replace(/[\s().\-/]/g, '').trim();

  if (digits.startsWith('00')) {
    digits = '+' + digits.slice(2);
  }

  return digits;
}

export function phoneProblem(value: string | null | undefined): string | null {

  const phone = normalisePhone(value);

  if (!phone) {
    return null;
  }

  if (!phone.startsWith('+')) {
    return 'Start with the country code, like +383 44 123 456. Without it the '
      + 'number cannot be dialled from abroad.';
  }

  const digits = phone.slice(1);

  if (!/^\d+$/.test(digits)) {
    return 'A telephone number is digits only after the country code.';
  }

  if (digits.length > 15) {
    return `A telephone number is at most 15 digits including the country `
      + `code; this one has ${digits.length}.`;
  }

  // Longest match first: 1 and 383 both begin a valid number.
  let code: string | null = null;

  for (let length = 3; length >= 1; length--) {
    const candidate = digits.slice(0, length);
    if (NATIONAL_LENGTHS[candidate]) {
      code = candidate;
      break;
    }
  }

  if (!code) {
    return `+${digits.slice(0, 3)} is not a country calling code this bank `
      + `recognises.`;
  }

  const national = digits.length - code.length;
  const expected = NATIONAL_LENGTHS[code];

  if (!expected.includes(national)) {
    return `A +${code} number has ${expected.join(' or ')} digits after the `
      + `country code; this one has ${national}.`;
  }

  return null;
}

/**
 * Letters, marks, and the punctuation names contain.
 *
 * Unicode-aware deliberately: Gëzim Çela and Behxhet Krasniqi are ordinary
 * Kosovo names, and an ASCII-only rule would reject much of the customer base.
 */
const NAME = /^[\p{L}\p{M}][\p{L}\p{M}'\-. ]*$/u;

export function nameProblem(
  value: string | null | undefined,
  label: string,
): string | null {

  const name = (value ?? '').trim();

  if (!name) {
    return null;
  }

  if (name.length < 2) {
    return `${label} is too short to be a name.`;
  }

  return NAME.test(name)
    ? null
    : `${label} contains something that is not part of a name. Letters, `
      + `spaces, apostrophes and hyphens only.`;
}

export function emailProblem(value: string | null | undefined): string | null {

  const email = (value ?? '').trim();

  if (!email) {
    return null;
  }

  if (email.length > 254) {
    return 'An email address is at most 254 characters.';
  }

  // Stricter than the browser's own check, which accepts "a@b".
  return /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/.test(email)
    ? null
    : 'That is not an email address that could receive mail.';
}

const MINIMUM_AGE = 18;
const MAXIMUM_AGE = 120;

export function dateOfBirthProblem(value: string | null | undefined): string | null {

  if (!value) {
    return null;
  }

  const born = new Date(value);

  if (Number.isNaN(born.getTime())) {
    return 'That is not a date.';
  }

  const today = new Date();

  if (born >= today) {
    return 'A date of birth cannot be today or in the future.';
  }

  let age = today.getFullYear() - born.getFullYear();
  const month = today.getMonth() - born.getMonth();

  if (month < 0 || (month === 0 && today.getDate() < born.getDate())) {
    age--;
  }

  if (age < MINIMUM_AGE) {
    return `An account holder must be at least ${MINIMUM_AGE}. This makes them `
      + `${age}.`;
  }

  if (age > MAXIMUM_AGE) {
    return `This makes them ${age} years old, which is a typing mistake rather `
      + `than a customer.`;
  }

  return null;
}

export function registrationProblem(
  value: string | null | undefined,
): string | null {

  const registration = (value ?? '').trim();

  if (!registration) {
    return null;
  }

  return /^[A-Za-z0-9-]{5,20}$/.test(registration)
    ? null
    : 'A registration number is 5 to 20 letters, digits or hyphens.';
}

/**
 * ISO 3166-1 alpha-2, the only country notation the scheme accepts.
 *
 * A literal list rather than anything derived at runtime. An earlier version
 * built this from Intl.supportedValuesOf('region'), which is not a key that
 * function accepts -- it threw while the module was still loading, and because
 * the router imports every screen eagerly, a throw there took the whole
 * application down to a blank page. Module-level code that can fail is not
 * worth the convenience.
 *
 * XK is included: Kosovo's code is user-assigned rather than official, and a
 * validator that refused it would refuse this bank's own country.
 */
const COUNTRIES = new Set<string>(
  ('AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI '
    + 'BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM '
    + 'CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI '
    + 'FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW '
    + 'GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG '
    + 'KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD '
    + 'ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE '
    + 'NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW '
    + 'PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS '
    + 'ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG '
    + 'UM US UY UZ VA VC VE VG VI VN VU WF WS XK YE YT ZA ZM ZW').split(' '),
);

export function countryProblem(
  value: string | null | undefined,
  label: string,
): string | null {

  const code = (value ?? '').trim().toUpperCase();

  if (!code) {
    return null;
  }

  return COUNTRIES.has(code)
    ? null
    : `${label} must be a two-letter country code such as XK, AL or DE; `
      + `"${code}" is not one.`;
}
