import { Navigate, useParams } from "react-router-dom";

export default function InvestigationReviewPage() {
  const { investigationId } = useParams();

  return (
    <Navigate
      to={`/investigations/${investigationId ?? ""}#review`}
      replace
    />
  );
}
