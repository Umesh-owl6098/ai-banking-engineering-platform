import api from "../api/api";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";

export async function getCustomers(
  filters?: {
    riskRating?: string;
    kycStatus?: string;
  },
): Promise<MockCustomer[]> {
  const response = await api.get<MockCustomer[]>(
    "/mock/customers",
    { params: filters },
  );

  return response.data;
}

export async function getCustomer(
  customerId: string,
): Promise<MockCustomer> {
  const response = await api.get<MockCustomer>(
    `/mock/customers/${customerId}`,
  );

  return response.data;
}

export async function getCustomerTransactions(
  customerId: string,
): Promise<MockTransaction[]> {
  const response = await api.get<MockTransaction[]>(
    `/mock/customers/${customerId}/transactions`,
  );

  return response.data;
}

export async function getTransaction(
  transactionId: string,
): Promise<MockTransaction> {
  const response = await api.get<MockTransaction>(
    `/mock/transactions/${transactionId}`,
  );

  return response.data;
}

export async function getFlaggedTransactions(): Promise<
  MockTransaction[]
> {
  const response = await api.get<MockTransaction[]>(
    "/mock/transactions/flagged",
  );

  return response.data;
}

export async function getTransactionsAboveRiskScore(
  minimumRiskScore = 0,
): Promise<MockTransaction[]> {
  const response = await api.get<MockTransaction[]>(
    "/mock/transactions",
    { params: { minimumRiskScore } },
  );

  return response.data;
}
