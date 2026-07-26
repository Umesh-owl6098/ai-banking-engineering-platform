import { useEffect, useMemo, useState } from "react";
import {
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
} from "@mui/material";
import { useNavigate } from "react-router-dom";

import EmptyState from "../components/ui/EmptyState";
import ErrorState from "../components/ui/ErrorState";
import { TableSkeleton } from "../components/ui/LoadingState";
import PageContainer from "../components/ui/PageContainer";
import PageHeader from "../components/ui/PageHeader";
import StatusChip from "../components/ui/StatusChip";
import { getCustomers } from "../services/mockBankingService";
import { layout, table } from "../theme/tokens";
import type { MockCustomer } from "../types/mockBanking";

const pageSizeOptions = [5, 10, 20];

function kycChipColor(
  kycStatus: string,
): "default" | "success" | "warning" | "error" {
  if (kycStatus === "VERIFIED") {
    return "success";
  }

  if (kycStatus === "FAILED") {
    return "error";
  }

  return "warning";
}

export default function CustomersPage() {
  const navigate = useNavigate();
  const [customers, setCustomers] = useState<MockCustomer[]>([]);
  const [riskRating, setRiskRating] = useState("");
  const [kycStatus, setKycStatus] = useState("");
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let isCurrent = true;

    async function loadCustomers(): Promise<void> {
      try {
        setIsLoading(true);
        setError(null);

        const filters = riskRating
          ? { riskRating }
          : kycStatus
            ? { kycStatus }
            : undefined;

        const data = await getCustomers(filters);

        if (isCurrent) {
          setCustomers(data);
          setPage(0);
        }
      } catch (caughtError) {
        if (isCurrent) {
          setError(caughtError);
        }
      } finally {
        if (isCurrent) {
          setIsLoading(false);
        }
      }
    }

    void loadCustomers();

    return () => {
      isCurrent = false;
    };
  }, [riskRating, kycStatus, reloadToken]);

  const visibleCustomers = useMemo(
    () =>
      customers.slice(
        page * rowsPerPage,
        page * rowsPerPage + rowsPerPage,
      ),
    [customers, page, rowsPerPage],
  );

  return (
    <PageContainer>
      <Stack spacing={layout.sectionGap}>
        <PageHeader
          title="Customers"
          description="Review customer profiles, KYC status, and risk ratings."
        />

        <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="risk-rating-label">Risk Rating</InputLabel>
            <Select
              labelId="risk-rating-label"
              label="Risk Rating"
              value={riskRating}
              onChange={(event) => {
                setRiskRating(event.target.value);
                setKycStatus("");
              }}
            >
              <MenuItem value="">All risk ratings</MenuItem>
              <MenuItem value="LOW">Low</MenuItem>
              <MenuItem value="MEDIUM">Medium</MenuItem>
              <MenuItem value="HIGH">High</MenuItem>
            </Select>
          </FormControl>

          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="kyc-status-label">KYC Status</InputLabel>
            <Select
              labelId="kyc-status-label"
              label="KYC Status"
              value={kycStatus}
              onChange={(event) => {
                setKycStatus(event.target.value);
                setRiskRating("");
              }}
            >
              <MenuItem value="">All KYC statuses</MenuItem>
              <MenuItem value="VERIFIED">Verified</MenuItem>
              <MenuItem value="PENDING">Pending</MenuItem>
              <MenuItem value="FAILED">Failed</MenuItem>
              <MenuItem value="EXPIRED">Expired</MenuItem>
            </Select>
          </FormControl>
        </Stack>

        {error != null && (
          <ErrorState
            error={error}
            fallback="Unable to load customers."
            onRetry={() => setReloadToken((current) => current + 1)}
          />
        )}

        <Paper variant="outlined" sx={{ overflow: "hidden" }}>
          {isLoading ? (
            <TableSkeleton rows={6} />
          ) : (
            <>
              <TableContainer sx={{ maxWidth: "100%", overflowX: "auto" }}>
                <Table size={table.size} stickyHeader={table.stickyHeader}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Account Number</TableCell>
                      <TableCell>KYC Status</TableCell>
                      <TableCell>Risk Rating</TableCell>
                      <TableCell>PEP Status</TableCell>
                      <TableCell>Account Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {visibleCustomers.map((customer) => (
                      <TableRow
                        hover
                        key={customer.id}
                        onClick={() => navigate(`/customers/${customer.id}`)}
                        sx={{ cursor: "pointer" }}
                      >
                        <TableCell>{customer.fullName}</TableCell>
                        <TableCell>{customer.accountNumber}</TableCell>
                        <TableCell>
                          <Chip
                            label={customer.kycStatus}
                            size="small"
                            color={kycChipColor(customer.kycStatus)}
                          />
                        </TableCell>
                        <TableCell>
                          <StatusChip kind="severity" value={customer.riskRating} />
                        </TableCell>
                        <TableCell>{customer.pepStatus}</TableCell>
                        <TableCell>{customer.accountStatus}</TableCell>
                      </TableRow>
                    ))}
                    {visibleCustomers.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <EmptyState
                            title="No customers found"
                            description="Adjust filters to see matching customer profiles."
                          />
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>

              <TablePagination
                component="div"
                count={customers.length}
                page={page}
                rowsPerPage={rowsPerPage}
                rowsPerPageOptions={pageSizeOptions}
                onPageChange={(_, nextPage) => setPage(nextPage)}
                onRowsPerPageChange={(event) => {
                  setRowsPerPage(Number(event.target.value));
                  setPage(0);
                }}
              />
            </>
          )}
        </Paper>
      </Stack>
    </PageContainer>
  );
}
