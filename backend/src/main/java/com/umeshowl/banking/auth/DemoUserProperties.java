package com.umeshowl.banking.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.demo-users")
public class DemoUserProperties {

    private boolean enabled = false;
    private String password = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
