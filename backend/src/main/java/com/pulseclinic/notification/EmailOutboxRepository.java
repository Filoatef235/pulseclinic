package com.pulseclinic.notification;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class EmailOutboxRepository {
    private final JdbcTemplate jdbc;
    public EmailOutboxRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void queue(String email, String template, UUID appointmentId) {
        jdbc.update("insert into email_outbox (to_email, template, payload) values (?, ?, jsonb_build_object('appointmentId', ?::text))", email, template, appointmentId.toString());
    }
    public void queueWaitlistOffer(String email, UUID waitlistId) {
        jdbc.update("insert into email_outbox (to_email, template, payload) values (?, 'WAITLIST_OPENING_OFFER', jsonb_build_object('waitlistId', ?::text))", email, waitlistId.toString());
    }
    public List<OutboxMessage> pending() { return jdbc.query("select id,to_email,template from email_outbox where status='PENDING' and (next_attempt_at is null or next_attempt_at<=now()) order by created_at limit 25",(rs,i)->new OutboxMessage(rs.getObject("id",UUID.class),rs.getString("to_email"),rs.getString("template"))); }
    public void sent(UUID id) { jdbc.update("update email_outbox set status='SENT',sent_at=now(),attempts=attempts+1,last_error=null where id=?",id); }
    public void failed(UUID id,String error) { jdbc.update("update email_outbox set status='FAILED',attempts=attempts+1,last_error=?,next_attempt_at=now()+interval '5 minutes' where id=?",error,id); }
    public record OutboxMessage(UUID id,String email,String template) {}
}
