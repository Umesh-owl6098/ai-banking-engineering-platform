import { Navigate, useParams } from "react-router-dom";

export default function InvestigationExplainabilityPage() {
  const { investigationId } = useParams();

  return (
    <Navigate
      to={`/investigations/${investigationId ?? ""}#explainability`}
      replace
    />
  );
}
