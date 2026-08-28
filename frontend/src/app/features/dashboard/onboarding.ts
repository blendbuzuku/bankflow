import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  countryProblem,
  dateOfBirthProblem,
  emailProblem,
  nameProblem,
  phoneProblem,
  registrationProblem,
} from '../../core/validation/client';
import { schemeTextProblem } from '../../core/validation/iban';
import {
  COUNTRIES_COMMON,
  COUNTRIES_REST,
} from '../../core/validation/countries';

import { Toasts } from '../../core/services/toasts';

import {
  AccountService,
  BeneficialOwnerRequest,
  ClientResponse,
  ClientType,
  IdentityDocumentType,
  LegalForm,
  SourceOfFunds,
} from '../../core/services/account';

/**
 * Registering as a bank client.
 *
 * Signing in and being a customer of the bank are different things: the account
 * gets you through the door, this is the KYC record that lets accounts be
 * opened in your name. Until it exists there is nothing to hold money.
 */
@Component({
  selector: 'app-onboarding',
  imports: [FormsModule],
  templateUrl: './onboarding.html',
  styleUrl: './onboarding.css',
})
export class Onboarding {

  private readonly accountService = inject(AccountService);
  private readonly toasts = inject(Toasts);

  /** Emitted so the dashboard can reload once a profile exists. */
  readonly created = output<ClientResponse>();

  clientType: ClientType = 'INDIVIDUAL';

  firstName = '';
  lastName = '';
  dateOfBirth = '';

  legalName = '';
  registrationNumber = '';
  taxNumber = '';
  industry = '';

  email = '';
  phone = '';

  // where they are
  addressLine1 = '';
  addressLine2 = '';
  city = '';
  postalCode = '';
  country = 'XK';

  // who they are
  placeOfBirth = '';
  countryOfBirth = 'XK';
  nationality = 'XK';
  countryOfResidence = 'XK';

  identityDocumentType: IdentityDocumentType | '' = '';
  identityDocumentNumber = '';
  identityDocumentCountry = 'XK';
  identityDocumentExpiry = '';
  personalNumber = '';

  // risk
  politicallyExposed = false;
  pepDetails = '';
  sourceOfFunds: SourceOfFunds | '' = '';
  sourceOfFundsDetail = '';

  // the company
  legalForm: LegalForm | '' = '';
  dateOfIncorporation = '';
  naceCode = '';
  beneficialOwners: BeneficialOwnerRequest[] = [];

  /*
   * Countries are chosen, not typed. A two-letter code is exactly the kind of
   * field somebody gets subtly wrong -- and unlike an IBAN it carries no
   * checksum, so a wrong-but-real code is indistinguishable from a right one.
   *
   * The ones this bank's customers actually pick come first; the rest follow
   * alphabetically.
   */
  readonly commonCountries = COUNTRIES_COMMON;
  readonly otherCountries = COUNTRIES_REST;

  readonly documentTypes: { value: IdentityDocumentType; label: string }[] = [
    { value: 'PASSPORT', label: 'Passport' },
    { value: 'NATIONAL_ID', label: 'National identity card' },
    { value: 'RESIDENCE_PERMIT', label: 'Residence permit' },
  ];

  readonly sourcesOfFunds: { value: SourceOfFunds; label: string }[] = [
    { value: 'SALARY', label: 'Salary or wages' },
    { value: 'BUSINESS_INCOME', label: 'Business income' },
    { value: 'PENSION', label: 'Pension' },
    { value: 'SAVINGS', label: 'Existing savings' },
    { value: 'INVESTMENTS', label: 'Investment returns' },
    { value: 'PROPERTY_SALE', label: 'Sale of property' },
    { value: 'INHERITANCE', label: 'Inheritance or gift' },
    { value: 'REMITTANCES', label: 'Remittances from family abroad' },
    { value: 'OTHER', label: 'Other' },
  ];

  readonly legalForms: { value: LegalForm; label: string }[] = [
    { value: 'SOLE_PROPRIETORSHIP', label: 'Individual business (B.I.)' },
    { value: 'GENERAL_PARTNERSHIP', label: 'General partnership (O.P.)' },
    { value: 'LIMITED_PARTNERSHIP', label: 'Limited partnership (Sh.K.M.)' },
    { value: 'LIMITED_LIABILITY', label: 'Limited liability company (Sh.P.K.)' },
    { value: 'JOINT_STOCK', label: 'Joint stock company (Sh.A.)' },
    { value: 'FOREIGN_BRANCH', label: 'Branch of a foreign company' },
    { value: 'NGO', label: 'Non-governmental organisation' },
    { value: 'OTHER', label: 'Other' },
  ];

  /**
   * The share at or above which somebody must be named.
   *
   * Shown on the form because the question "do I have to list this person?"
   * is the one people get wrong, and the answer is a number.
   */
  readonly disclosureThreshold = 25;

  readonly saving = signal(false);
  readonly error = signal('');

  isBusiness(): boolean {
    return this.clientType === 'BUSINESS';
  }

  /*
   * The same rules the server applies. Onboarding is filled in once, often
   * with a teller waiting, so being told which digit is wrong beats being
   * told the record was refused.
   */

  firstNameError(): string | null {
    return nameProblem(this.firstName, 'First name');
  }

  lastNameError(): string | null {
    return nameProblem(this.lastName, 'Last name');
  }

  emailError(): string | null {
    return emailProblem(this.email);
  }

  phoneError(): string | null {
    return phoneProblem(this.phone);
  }

  dateOfBirthError(): string | null {
    return dateOfBirthProblem(this.dateOfBirth);
  }

  registrationError(): string | null {
    return registrationProblem(this.registrationNumber);
  }

  addressError(): string | null {
    return schemeTextProblem(this.addressLine1, 'The street address', 70)
      ?? schemeTextProblem(this.addressLine2, 'The second address line', 70);
  }

  cityError(): string | null {
    return schemeTextProblem(this.city, 'The town or city', 35);
  }

  countryError(): string | null {
    return countryProblem(this.country, 'Country');
  }

  nationalityError(): string | null {
    return countryProblem(this.nationality, 'Nationality');
  }

  residenceError(): string | null {
    return countryProblem(this.countryOfResidence, 'Country of residence');
  }

  documentCountryError(): string | null {
    return countryProblem(this.identityDocumentCountry, 'The issuing country');
  }

  documentNumberError(): string | null {

    const number = this.identityDocumentNumber.trim();

    if (!number) {
      return null;
    }

    return /^[A-Za-z0-9-]{4,40}$/.test(number)
      ? null
      : 'A document number is 4 to 40 letters, digits or hyphens.';
  }

  /** An expired document identifies nobody -- that is what expiry means. */
  documentExpiryError(): string | null {

    if (!this.identityDocumentExpiry) {
      return null;
    }

    const expiry = new Date(this.identityDocumentExpiry);

    if (Number.isNaN(expiry.getTime())) {
      return 'That is not a date.';
    }

    return expiry > new Date()
      ? null
      : 'That document has expired. Ask for a current one.';
  }

  personalNumberError(): string | null {

    const number = this.personalNumber.trim();

    if (!number) {
      return null;
    }

    return /^[0-9]{8,20}$/.test(number)
      ? null
      : 'A personal number is 8 to 20 digits.';
  }

  incorporationError(): string | null {

    if (!this.dateOfIncorporation) {
      return null;
    }

    return new Date(this.dateOfIncorporation) > new Date()
      ? 'A company cannot have been incorporated in the future.'
      : null;
  }

  naceError(): string | null {

    const nace = this.naceCode.trim().toUpperCase();

    if (!nace) {
      return null;
    }

    return /^[A-U](\d{2}(\.\d{1,2})?)?$/.test(nace)
      ? null
      : 'A NACE code is a letter and up to four digits, such as G47.11.';
  }

  // --- the people behind a company -----------------------------------

  addOwner(): void {
    this.beneficialOwners.push({
      fullName: '',
      dateOfBirth: '',
      nationality: 'XK',
      countryOfResidence: 'XK',
      ownershipPercentage: 0,
      controlsByOtherMeans: false,
      politicallyExposed: false,
    });
  }

  removeOwner(index: number): void {
    this.beneficialOwners.splice(index, 1);
  }

  declaredOwnership(): number {
    return this.beneficialOwners.reduce(
      (total, owner) => total + (Number(owner.ownershipPercentage) || 0), 0,
    );
  }

  /**
   * What is wrong with the declared owners, as one message.
   *
   * The sum matters as much as each entry: owners adding up to more than the
   * whole company is an entry mistake, and owners adding up to very little
   * leaves the rest of the company unexplained.
   */
  ownersError(): string | null {

    if (!this.isBusiness()) {
      return null;
    }

    if (!this.beneficialOwners.length) {
      return `Name the people who own or control this company. Anyone holding `
        + `${this.disclosureThreshold}% or more has to be recorded.`;
    }

    for (const owner of this.beneficialOwners) {

      if (!owner.fullName.trim() || !owner.dateOfBirth) {
        return 'Every beneficial owner needs a name and a date of birth.';
      }

      const problem = nameProblem(owner.fullName, "The owner's name")
        ?? dateOfBirthProblem(owner.dateOfBirth);

      if (problem) {
        return problem;
      }

      const share = Number(owner.ownershipPercentage);

      if (Number.isNaN(share) || share < 0 || share > 100) {
        return 'A shareholding is between 0 and 100 percent.';
      }

      if (share === 0 && !owner.controlsByOtherMeans) {
        return `${owner.fullName.trim()} holds nothing and controls the company `
          + `by no other means, so they are not a beneficial owner.`;
      }
    }

    if (this.declaredOwnership() > 100) {
      return `The declared shareholdings add up to ${this.declaredOwnership()}%, `
        + `which is more than the whole company.`;
    }

    return null;
  }

  canSubmit(): boolean {

    /*
     * Both are required now rather than optional. A bank has to be able to
     * telephone somebody about a payment it has stopped, and a date of birth
     * is how two customers with the same name are told apart.
     */
    if (!this.email || !this.phone) {
      return false;
    }

    if (this.emailError() || this.phoneError()) {
      return false;
    }

    // Everybody has an address, and it travels with every payment they make.
    if (!this.addressLine1 || !this.city || !this.country) {
      return false;
    }

    if (this.addressError() || this.cityError() || this.countryError()) {
      return false;
    }

    if (!this.sourceOfFunds) {
      return false;
    }

    if (this.politicallyExposed && !this.pepDetails.trim()) {
      return false;
    }

    return this.isBusiness() ? this.canSubmitBusiness() : this.canSubmitPerson();
  }

  private canSubmitPerson(): boolean {

    const complete = !!(this.firstName && this.lastName && this.dateOfBirth
      && this.placeOfBirth && this.nationality && this.countryOfResidence
      && this.identityDocumentType && this.identityDocumentNumber
      && this.identityDocumentCountry && this.identityDocumentExpiry);

    return complete
      && !this.firstNameError()
      && !this.lastNameError()
      && !this.dateOfBirthError()
      && !this.nationalityError()
      && !this.residenceError()
      && !this.documentNumberError()
      && !this.documentCountryError()
      && !this.documentExpiryError()
      && !this.personalNumberError();
  }

  private canSubmitBusiness(): boolean {

    const complete = !!(this.legalName && this.registrationNumber
      && this.legalForm && this.dateOfIncorporation);

    return complete
      && !this.registrationError()
      && !this.incorporationError()
      && !this.naceError()
      && !this.ownersError();
  }

  submit(): void {

    this.error.set('');
    this.saving.set(true);

    this.accountService.createClient({
      clientType: this.clientType,
      firstName: this.isBusiness() ? undefined : this.firstName,
      lastName: this.isBusiness() ? undefined : this.lastName,
      dateOfBirth: this.isBusiness() || !this.dateOfBirth
        ? undefined
        : this.dateOfBirth,
      legalName: this.isBusiness() ? this.legalName : undefined,
      registrationNumber: this.isBusiness() ? this.registrationNumber : undefined,
      taxNumber: this.isBusiness() ? this.taxNumber || undefined : undefined,
      industry: this.isBusiness() ? this.industry || undefined : undefined,
      email: this.email,
      phone: this.phone,

      addressLine1: this.addressLine1,
      addressLine2: this.addressLine2 || undefined,
      city: this.city,
      postalCode: this.postalCode || undefined,
      country: this.country,

      placeOfBirth: this.isBusiness() ? undefined : this.placeOfBirth,
      countryOfBirth: this.isBusiness() ? undefined : this.countryOfBirth,
      nationality: this.isBusiness() ? undefined : this.nationality,
      countryOfResidence:
        this.isBusiness() ? undefined : this.countryOfResidence,

      identityDocumentType: this.isBusiness() || !this.identityDocumentType
        ? undefined
        : this.identityDocumentType,
      identityDocumentNumber:
        this.isBusiness() ? undefined : this.identityDocumentNumber,
      identityDocumentCountry:
        this.isBusiness() ? undefined : this.identityDocumentCountry,
      identityDocumentExpiry:
        this.isBusiness() ? undefined : this.identityDocumentExpiry,
      personalNumber:
        this.isBusiness() ? undefined : this.personalNumber || undefined,

      politicallyExposed: this.politicallyExposed,
      pepDetails: this.pepDetails || undefined,
      sourceOfFunds: this.sourceOfFunds || undefined,
      sourceOfFundsDetail: this.sourceOfFundsDetail || undefined,

      legalForm: this.isBusiness() && this.legalForm ? this.legalForm : undefined,
      dateOfIncorporation:
        this.isBusiness() ? this.dateOfIncorporation : undefined,
      naceCode: this.isBusiness() ? this.naceCode || undefined : undefined,
      beneficialOwners: this.isBusiness() ? this.beneficialOwners : undefined,
    }).subscribe({
      next: client => {
        this.saving.set(false);
        this.toasts.success(
          'Your details have been sent to the branch',
          'Once someone has checked them, an account can be opened for you.',
        );
        this.created.emit(client);
      },
      error: error => {
        this.saving.set(false);
        this.toasts.failure('Your profile could not be created', error);
        this.error.set(
          error?.error?.message ?? 'Your profile could not be created.',
        );
      },
    });
  }
}
