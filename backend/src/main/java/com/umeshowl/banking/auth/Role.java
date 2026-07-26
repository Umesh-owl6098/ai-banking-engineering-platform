package com.umeshowl.banking.auth;

public enum Role {
    ADMIN,
    SUPERVISOR,
    FRAUD_ANALYST,
    COMPLIANCE_ANALYST,
    READ_ONLY;

    public String authority() {
        return "ROLE_" + name();
    }
}
