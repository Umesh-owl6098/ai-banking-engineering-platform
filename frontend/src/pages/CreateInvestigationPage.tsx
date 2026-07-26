import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { Navigate, useLocation, useNavigate } from "react-router-dom";

import { useAuth } from "../hooks/useAuth";
import {
  createInvestigation,
  INVESTIGATION_PROJECT_ID,
} from "../services/investigationService";
import {
  getCustomerTransactions,
  getCustomers,
  getTransaction,
} from "../services/mockBankingService";
import { getProjects } from "../services/projectService";
import type {
  MockCustomer,
  MockTransaction,
} from "../types/mockBanking";
import type { Project } from "../types/project";
import { getApiErrorMessage } from "../utils/apiError";

interface CreateInvestigationLocationState {
  customerId?: string;
  transactionId?: string;
  projectId?: string;
}

export default function CreateInvestigationPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { canCreateInvestigation } = useAuth();
  const routeState =
    (location.state as CreateInvestigationLocationState | null) ??
    {};
  const [projectId, setProjectId] = useState(
    routeState.projectId ?? INVESTIGATION_PROJECT_ID,
  );
  const [customerId, setCustomerId] = useState(
    routeState.customerId ?? "",
  );
  const [transactionId, setTransactionId] = useState(
    routeState.transactionId ?? "",
  );
  const [caseType, setCaseType] = useState("FRAUD");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [projects, setProjects] = useState<Project[]>([]);
  const [customers, setCustomers] = useState<MockCustomer[]>([]);
  const [transactions, setTransactions] = useState<MockTransaction[]>([]);
  const [isLoadingOptions, setIsLoadingOptions] = useState(true);
  const [isLoadingTransactions, setIsLoadingTransactions] =
    useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === projectId) ?? null,
    [projectId, projects],
  );
  const selectedCustomer = useMemo(
    () =>
      customers.find((customer) => customer.id === customerId) ?? null,
    [customerId, customers],
  );
  const selectedTransaction = useMemo(
    () =>
      transactions.find(
        (transaction) => transaction.id === transactionId,
      ) ?? null,
    [transactionId, transactions],
  );

  useEffect(() => {
    let isCurrent = true;

    async function loadInitialOptions(): Promise<void> {
      try {
        setIsLoadingOptions(true);
        setLoadError(null);
        const [projectData, customerData] = await Promise.all([
          getProjects(),
          getCustomers(),
        ]);

        if (isCurrent) {
          setProjects(projectData);
          setCustomers(customerData);
        }
      } catch (error) {
        if (isCurrent) {
          setLoadError(
            getApiErrorMessage(
              error,
              "Unable to load project and customer options.",
            ),
          );
        }
      } finally {
        if (isCurrent) {
          setIsLoadingOptions(false);
        }
      }
    }

    void loadInitialOptions();
    return () => {
      isCurrent = false;
    };
  }, []);

  useEffect(() => {
    let isCurrent = true;

    async function loadTransactions(): Promise<void> {
      if (!customerId) {
        setTransactions([]);
        return;
      }

      try {
        setIsLoadingTransactions(true);
        const data = await getCustomerTransactions(customerId);
        if (isCurrent) {
          setTransactions(data);
        }
      } catch (error) {
        if (isCurrent) {
          setLoadError(
            getApiErrorMessage(
              error,
              "Unable to load customer transactions.",
            ),
          );
        }
      } finally {
        if (isCurrent) {
          setIsLoadingTransactions(false);
        }
      }
    }

    void loadTransactions();
    return () => {
      isCurrent = false;
    };
  }, [customerId]);

  useEffect(() => {
    let isCurrent = true;

    async function resolvePrefilledTransaction(): Promise<void> {
      if (!routeState.transactionId || routeState.customerId) {
        return;
      }

      try {
        const transaction = await getTransaction(
          routeState.transactionId,
        );
        if (isCurrent) {
          setCustomerId(transaction.customerId);
        }
      } catch (error) {
        if (isCurrent) {
          setLoadError(
            getApiErrorMessage(
              error,
              "Unable to resolve the selected transaction.",
            ),
          );
        }
      }
    }

    void resolvePrefilledTransaction();
    return () => {
      isCurrent = false;
    };
  }, [routeState.customerId, routeState.transactionId]);

  function validate(): boolean {
    const validationErrors: Record<string, string> = {};

    if (!selectedProject) validationErrors.project = "Select a project.";
    if (!customerId && !transactionId) {
      validationErrors.subject =
        "Select a customer, transaction, or both.";
    }
    if (
      selectedTransaction &&
      customerId !== selectedTransaction.customerId
    ) {
      validationErrors.subject =
        "The selected transaction does not belong to the selected customer.";
    }
    if (!title.trim()) validationErrors.title = "Title is required.";
    if (!description.trim()) {
      validationErrors.description = "Description is required.";
    }

    setErrors(validationErrors);
    return Object.keys(validationErrors).length === 0;
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> {
    event.preventDefault();
    setSubmitError(null);
    if (!validate()) return;

    try {
      setIsSubmitting(true);
      const investigation = await createInvestigation({
        projectId,
        customerId: customerId || undefined,
        transactionId: transactionId || undefined,
        caseType,
        title: title.trim(),
        description: description.trim(),
        priority,
      });
      navigate(`/investigations/${investigation.id}`);
    } catch (error) {
      setSubmitError(
        getApiErrorMessage(
          error,
          "Unable to create the investigation.",
        ),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!canCreateInvestigation) {
    return <Navigate to="/investigations" replace />;
  }

  return (
    <Stack spacing={3}>
      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate(-1)}
        sx={{ alignSelf: "flex-start" }}
      >
        Back
      </Button>

      <Box>
        <Typography variant="h4" gutterBottom>
          Create Investigation
        </Typography>
        <Typography color="text.secondary">
          Select the investigation subject without entering raw identifiers.
        </Typography>
      </Box>

      <Box component="form" noValidate onSubmit={(event) => void handleSubmit(event)}>
        <Paper variant="outlined" sx={{ maxWidth: 760, p: 3 }}>
          <Stack spacing={2}>
            {loadError && <Alert severity="error">{loadError}</Alert>}
            {submitError && <Alert severity="error">{submitError}</Alert>}
            {errors.subject && (
              <Alert severity="warning">{errors.subject}</Alert>
            )}

            <Autocomplete
              options={projects}
              value={selectedProject}
              loading={isLoadingOptions}
              getOptionLabel={(project) => project.name}
              isOptionEqualToValue={(a, b) => a.id === b.id}
              onChange={(_, project) => {
                setProjectId(project?.id ?? "");
                setErrors((current) => ({ ...current, project: "" }));
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Project"
                  error={Boolean(errors.project)}
                  helperText={errors.project}
                />
              )}
              noOptionsText="No projects available."
              loadingText="Loading projects..."
            />

            <Autocomplete
              options={customers}
              value={selectedCustomer}
              loading={isLoadingOptions}
              getOptionLabel={(customer) =>
                `${customer.fullName} — ${customer.accountNumber} — KYC ${customer.kycStatus} — ${customer.riskRating} risk`
              }
              isOptionEqualToValue={(a, b) => a.id === b.id}
              onChange={(_, customer) => {
                const nextCustomerId = customer?.id ?? "";
                setCustomerId(nextCustomerId);
                if (
                  selectedTransaction &&
                  selectedTransaction.customerId !== nextCustomerId
                ) {
                  setTransactionId("");
                }
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Customer"
                  helperText="Search by customer name, account number, or risk rating."
                />
              )}
              noOptionsText="No customers found."
              loadingText="Loading customers..."
            />

            <Autocomplete
              options={transactions}
              value={selectedTransaction}
              loading={isLoadingTransactions}
              disabled={!customerId}
              getOptionLabel={(transaction) =>
                `${transaction.transactionReference} — ${new Intl.NumberFormat("en-US", {
                  style: "currency",
                  currency: transaction.currency,
                }).format(transaction.amount)} ${transaction.currency} — ${new Date(transaction.transactionDate).toLocaleDateString()} — ${transaction.flagged ? "FLAGGED" : "not flagged"} — risk ${transaction.riskScore ?? "—"}`
              }
              isOptionEqualToValue={(a, b) => a.id === b.id}
              onChange={(_, transaction) => {
                setTransactionId(transaction?.id ?? "");
                if (transaction) setCustomerId(transaction.customerId);
              }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Transaction"
                  helperText={
                    customerId
                      ? "Only transactions for the selected customer are shown."
                      : "Select a customer to load transactions."
                  }
                />
              )}
              noOptionsText={
                customerId
                  ? "No transactions found for this customer."
                  : "Select a customer first."
              }
              loadingText="Loading transactions..."
            />

            <FormControl fullWidth>
              <InputLabel id="case-type-label">Case Type</InputLabel>
              <Select labelId="case-type-label" label="Case Type" value={caseType} onChange={(event) => setCaseType(event.target.value)}>
                <MenuItem value="FRAUD">Fraud</MenuItem>
                <MenuItem value="KYC">KYC</MenuItem>
                <MenuItem value="AML">AML</MenuItem>
                <MenuItem value="COMPLIANCE">Compliance</MenuItem>
              </Select>
            </FormControl>
            <TextField label="Title" value={title} onChange={(event) => setTitle(event.target.value)} error={Boolean(errors.title)} helperText={errors.title} fullWidth />
            <TextField label="Description" value={description} onChange={(event) => setDescription(event.target.value)} error={Boolean(errors.description)} helperText={errors.description} minRows={4} multiline fullWidth />
            <FormControl fullWidth>
              <InputLabel id="priority-label">Priority</InputLabel>
              <Select labelId="priority-label" label="Priority" value={priority} onChange={(event) => setPriority(event.target.value)}>
                <MenuItem value="LOW">Low</MenuItem>
                <MenuItem value="MEDIUM">Medium</MenuItem>
                <MenuItem value="HIGH">High</MenuItem>
                <MenuItem value="CRITICAL">Critical</MenuItem>
              </Select>
            </FormControl>
            <Button type="submit" variant="contained" disabled={isSubmitting || isLoadingOptions} sx={{ alignSelf: "flex-start" }}>
              {isSubmitting ? "Creating Investigation..." : "Create Investigation"}
            </Button>
          </Stack>
        </Paper>
      </Box>
    </Stack>
  );
}
