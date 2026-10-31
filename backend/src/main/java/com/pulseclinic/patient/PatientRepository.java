package com.pulseclinic.patient;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class PatientRepository {
    private final JdbcTemplate jdbc;
    PatientRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    List<PatientRow> search(String query) {
        String value = "%" + query.trim().toLowerCase() + "%";
        return jdbc.query("select id, full_name, phone from patients where lower(full_name) like ? or lower(coalesce(phone,'')) like ? order by full_name limit 20", (rs, i) -> new PatientRow(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("phone")), value, value);
    }
    UUID createWalkIn(String fullName, String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into patients (id, full_name, phone) values (?, ?, ?)", id, fullName, phone == null || phone.isBlank() ? null : phone.trim());
        return id;
    }
    record PatientRow(UUID id, String fullName, String phone) {}
}
