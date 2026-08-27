/**
 * Plain English for ISO 20022 element names.
 *
 * The XML is the record and stays exactly as it was sent, but almost nobody
 * reads `IntrBkSttlmAmt` faster than "interbank settlement amount". This maps
 * the elements this bank actually sends and receives; anything not listed
 * falls back to its own tag, so an unmapped element is unhelpful rather than
 * invisible.
 */
export const ISO_LABELS: Record<string, string> = {

  // --- envelopes and headers ---
  Document: 'Message',
  AppHdr: 'Business application header',
  GrpHdr: 'Group header',
  MsgId: 'Message identifier',
  CreDtTm: 'Created',
  NbOfTxs: 'Number of transactions',
  CtrlSum: 'Control sum',
  Fr: 'From',
  To: 'To',
  BizMsgIdr: 'Business message identifier',
  MsgDefIdr: 'Message definition',
  BizSvc: 'Business service',

  // --- message bodies ---
  FIToFICstmrCdtTrf: 'Customer credit transfer',
  FIToFIPmtStsRpt: 'Payment status report',
  PmtRtr: 'Payment return',
  FIToFIPmtCxlReq: 'Payment cancellation request',
  BkToCstmrStmt: 'Account statement',

  // --- transaction blocks ---
  CdtTrfTxInf: 'Credit transfer',
  TxInfAndSts: 'Transaction status',
  TxInf: 'Transaction',
  Undrlyg: 'Underlying transaction',
  PmtId: 'Payment identifiers',
  PmtTpInf: 'Payment type',

  // --- identifiers ---
  InstrId: 'Instruction identifier',
  EndToEndId: 'End-to-end reference',
  TxId: 'Transaction identifier',
  UETR: 'Unique end-to-end reference (UETR)',
  StsId: 'Status identifier',
  RtrId: 'Return identifier',
  CxlId: 'Cancellation identifier',
  OrgnlMsgId: 'Original message identifier',
  OrgnlMsgNmId: 'Original message type',
  OrgnlInstrId: 'Original instruction identifier',
  OrgnlEndToEndId: 'Original end-to-end reference',
  OrgnlTxId: 'Original transaction identifier',
  OrgnlUETR: 'Original UETR',
  OrgnlGrpInf: 'About the original message',
  OrgnlGrpInfAndSts: 'About the original message',
  OrgnlTxRef: 'The original payment',

  // --- money ---
  IntrBkSttlmAmt: 'Settlement amount',
  TtlIntrBkSttlmAmt: 'Total settlement amount',
  OrgnlIntrBkSttlmAmt: 'Original settlement amount',
  RtrdIntrBkSttlmAmt: 'Amount returned',
  TtlRtrdIntrBkSttlmAmt: 'Total returned',
  InstdAmt: 'Instructed amount',
  Amt: 'Amount',
  Sum: 'Sum',
  IntrBkSttlmDt: 'Settlement date',
  OrgnlIntrBkSttlmDt: 'Original settlement date',
  ChrgBr: 'Who pays the charges',
  ChrgsInf: 'Charges',

  // --- settlement and scheme ---
  SttlmInf: 'Settlement',
  SttlmMtd: 'Settlement method',
  ClrSys: 'Clearing system',
  SvcLvl: 'Service level',
  LclInstrm: 'Local instrument',
  CtgyPurp: 'Category purpose',
  Purp: 'Purpose',
  Cd: 'Code',
  Prtry: 'Proprietary code',
  Issr: 'Issued by',

  // --- parties ---
  Dbtr: 'Payer',
  DbtrAcct: "Payer's account",
  DbtrAgt: "Payer's bank",
  Cdtr: 'Beneficiary',
  CdtrAcct: "Beneficiary's account",
  CdtrAgt: "Beneficiary's bank",
  UltmtDbtr: 'Ultimate payer',
  UltmtCdtr: 'Ultimate beneficiary',
  InstgAgt: 'Instructing bank',
  InstdAgt: 'Instructed bank',
  Assgnmt: 'Case assignment',
  Assgnr: 'Raised by',
  Assgne: 'Sent to',
  Pty: 'Party',
  Nm: 'Name',
  Id: 'Identifier',
  Othr: 'Other identifier',
  PstlAdr: 'Address',
  Ctry: 'Country',
  FinInstnId: 'Financial institution',
  BICFI: 'BIC',
  IBAN: 'IBAN',
  Ownr: 'Account holder',
  Svcr: 'Account servicer',

  // --- outcome ---
  TxSts: 'Status',
  Sts: 'Status',
  StsRsnInf: 'Reason',
  RtrRsnInf: 'Reason for the return',
  CxlRsnInf: 'Reason for the request',
  Rsn: 'Reason',
  Orgtr: 'Raised by',
  AddtlInf: 'Additional information',
  RmtInf: 'Remittance information',
  Ustrd: 'Reference',

  // --- statement ---
  Stmt: 'Statement',
  StmtPgntn: 'Pagination',
  PgNb: 'Page',
  LastPgInd: 'Last page',
  FrToDt: 'Period',
  FrDtTm: 'From',
  ToDtTm: 'To',
  Acct: 'Account',
  Ccy: 'Currency',
  Bal: 'Balance',
  Tp: 'Type',
  CdOrPrtry: 'Code',
  CdtDbtInd: 'Direction',
  Dt: 'Date',
  DtTm: 'Date and time',
  TxsSummry: 'Totals',
  TtlNtries: 'All entries',
  TtlCdtNtries: 'Credits',
  TtlDbtNtries: 'Debits',
  NbOfNtries: 'Number of entries',
  Ntry: 'Entry',
  NtryRef: 'Entry reference',
  BookgDt: 'Booked',
  ValDt: 'Value date',
  BkTxCd: 'Bank transaction code',
  AddtlNtryInf: 'Detail',
};

/**
 * Plain English for the coded values, where the code alone says little.
 *
 * A reader who knows the scheme loses nothing — the code is still shown — but
 * one who does not should not have to keep a table open beside the screen.
 */
export const ISO_CODE_MEANINGS: Record<string, string> = {

  // charge bearer
  DEBT: 'payer pays all charges',
  CRED: 'beneficiary pays all charges',
  SHAR: 'charges shared',
  SLEV: 'charges follow the scheme',

  // service level
  SEPA: 'SEPA',
  URGP: 'urgent',
  NURG: 'non-urgent',

  // settlement method
  CLRG: 'cleared through a system',
  INDA: 'settled on the receiving bank’s books',
  INGA: 'settled on the sending bank’s books',
  COVE: 'covered by a separate payment',

  // status
  ACSC: 'accepted and settled',
  ACSP: 'accepted, being processed',
  ACCP: 'accepted',
  ACTC: 'technically accepted',
  RJCT: 'rejected',
  PDNG: 'pending',
  BOOK: 'booked',

  // direction
  CRDT: 'money in',
  DBIT: 'money out',

  // balance types
  OPBD: 'opening booked balance',
  CLBD: 'closing booked balance',
};
