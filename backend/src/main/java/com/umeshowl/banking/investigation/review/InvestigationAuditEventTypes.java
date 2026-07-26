package com.umeshowl.banking.investigation.review;

public final class InvestigationAuditEventTypes {

    public static final String CASE_CREATED = "CASE_CREATED";
    public static final String CASE_STATUS_CHANGED = "CASE_STATUS_CHANGED";
    public static final String SUPERVISOR_ROUTING = "SUPERVISOR_ROUTING";
    public static final String AGENT_FINDING_PRODUCED = "AGENT_FINDING_PRODUCED";
    public static final String COMPLIANCE_REVIEW_COMPLETE =
            "COMPLIANCE_REVIEW_COMPLETE";
    public static final String INVESTIGATION_COMPLETE = "INVESTIGATION_COMPLETE";
    public static final String HUMAN_DECISION = "HUMAN_DECISION";
    public static final String CASE_CLOSED = "CASE_CLOSED";
    public static final String ANALYST_NOTE = "ANALYST_NOTE";
    public static final String CLARIFICATION_REQUESTED = "CLARIFICATION_REQUESTED";
    public static final String INVESTIGATION_ASSIGNED = "INVESTIGATION_ASSIGNED";
    public static final String INVESTIGATION_CLAIMED = "INVESTIGATION_CLAIMED";
    public static final String INVESTIGATION_REASSIGNED = "INVESTIGATION_REASSIGNED";
    public static final String INVESTIGATION_UNASSIGNED = "INVESTIGATION_UNASSIGNED";
    public static final String ANALYST_REVIEW_STARTED = "ANALYST_REVIEW_STARTED";

    private InvestigationAuditEventTypes() {
    }
}
