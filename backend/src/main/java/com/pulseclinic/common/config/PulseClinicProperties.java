package com.pulseclinic.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseclinic")
public class PulseClinicProperties {

    private final Cors cors = new Cors();
    private final Auth auth = new Auth();
    private final Notifications notifications = new Notifications();

    public Cors getCors() {
        return cors;
    }

    public Auth getAuth() { return auth; }
    public Notifications getNotifications() { return notifications; }

    public static class Notifications {
        private String publicUrl = "http://localhost:3000";
        private String resendApiKey;
        private String from = "PulseClinic <onboarding@resend.dev>";
        public String getPublicUrl() { return publicUrl; }
        public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
        public String getResendApiKey() { return resendApiKey; }
        public void setResendApiKey(String resendApiKey) { this.resendApiKey = resendApiKey; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }

    public static class Auth {
        private String jwtSecret;
        private int accessTokenMinutes = 15;
        private int refreshTokenDays = 30;
        private boolean secureCookies;
        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public int getAccessTokenMinutes() { return accessTokenMinutes; }
        public void setAccessTokenMinutes(int accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }
        public int getRefreshTokenDays() { return refreshTokenDays; }
        public void setRefreshTokenDays(int refreshTokenDays) { this.refreshTokenDays = refreshTokenDays; }
        public boolean isSecureCookies() { return secureCookies; }
        public void setSecureCookies(boolean secureCookies) { this.secureCookies = secureCookies; }
    }

    public static class Cors {
        private List<String> origins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getOrigins() {
            return origins;
        }

        public void setOrigins(List<String> origins) {
            this.origins = origins;
        }
    }
}
