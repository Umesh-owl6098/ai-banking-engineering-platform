export interface MockCustomer {
  id: string;
  fullName: string;
  dateOfBirth: string;
  nationality: string | null;
  countryOfResidence: string;
  accountNumber: string;
  accountStatus: string;
  email: string | null;
  occupation: string | null;
  sourceOfFunds: string | null;
  kycStatus: string;
  riskRating: string;
  pepStatus: string;
  accountOpened: string;
  createdAt: string;
}

export interface MockTransaction {
  id: string;
  customerId: string;
  transactionReference: string;
  transactionDate: string;
  amount: number;
  currency: string;
  transactionType: string;
  transactionStatus: string;
  channel: string;
  counterpartyName: string | null;
  counterpartyBank: string | null;
  counterpartyCountry: string | null;
  originCountry: string | null;
  destinationCountry: string | null;
  description: string | null;
  flagged: boolean;
  riskScore: number | null;
  riskIndicators: string[] | null;
  createdAt: string;
}
