import { Card, CardContent, Grid, Typography } from "@mui/material";

import type { MockCustomer } from "../../types/mockBanking";
import SummaryField from "./SummaryField";

function formatAccountAge(accountOpened: string): string {
  const opened = new Date(accountOpened);
  const now = new Date();
  const years = Math.floor(
    (now.getTime() - opened.getTime()) / (365.25 * 24 * 60 * 60 * 1000),
  );

  if (years >= 1) {
    return `${years} year${years === 1 ? "" : "s"}`;
  }

  const months = Math.max(
    1,
    Math.floor(
      (now.getTime() - opened.getTime()) / (30 * 24 * 60 * 60 * 1000),
    ),
  );
  return `${months} month${months === 1 ? "" : "s"}`;
}

export default function CustomerRiskSummary({
  customer,
}: {
  customer: MockCustomer | null;
}) {
  return (
    <Card sx={{ height: "100%" }}>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Customer Risk Summary
        </Typography>
        {customer ? (
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="Customer Name" value={customer.fullName} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="Customer ID" value={customer.id} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField
                label="Account Age"
                value={formatAccountAge(customer.accountOpened)}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField
                label="Country"
                value={customer.countryOfResidence}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="KYC Status" value={customer.kycStatus} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="PEP Status" value={customer.pepStatus} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="Risk Level" value={customer.riskRating} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField
                label="Account Status"
                value={customer.accountStatus}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="Nationality" value={customer.nationality} />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <SummaryField label="Occupation" value={customer.occupation} />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <SummaryField
                label="Source Of Funds"
                value={customer.sourceOfFunds}
              />
            </Grid>
          </Grid>
        ) : (
          <Typography color="text.secondary">
            No customer is linked to this investigation.
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}
