package com.pulseclinic.auth;

import com.pulseclinic.common.config.PulseClinicProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
class AuthPropertiesValidator {
    private final PulseClinicProperties properties;
    AuthPropertiesValidator(PulseClinicProperties properties) { this.properties = properties; }
    @PostConstruct
    void validate() {
        String secret = properties.getAuth().getJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 characters.");
        }
    }
}
