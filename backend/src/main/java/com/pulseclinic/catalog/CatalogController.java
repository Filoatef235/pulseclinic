package com.pulseclinic.catalog;

import com.pulseclinic.catalog.dto.DoctorResponse;
import com.pulseclinic.catalog.dto.ServiceResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.CacheControl;
import com.pulseclinic.team.ProfilePhotoService;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final JdbcTemplate jdbc;
    private final ProfilePhotoService photos;
    public CatalogController(JdbcTemplate jdbc, ProfilePhotoService photos) { this.jdbc = jdbc; this.photos = photos; }
    @GetMapping("/services")
    public List<ServiceResponse> services() {
        return jdbc.query("select id, name, duration_minutes, price_cents from services where active order by name", (rs, i) -> new ServiceResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("duration_minutes"), rs.getInt("price_cents")));
    }
    @GetMapping("/doctors")
    public List<DoctorResponse> doctors(@RequestParam UUID serviceId) {
        return jdbc.query("select d.id, d.full_name, s.name specialty, d.bio, d.photo_storage_key from doctors d join specialties s on s.id = d.specialty_id join doctor_services ds on ds.doctor_id = d.id where d.active and ds.service_id = ? order by d.full_name", (rs, i) -> new DoctorResponse(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("specialty"), rs.getString("bio"), rs.getString("photo_storage_key") == null ? null : "/api/v1/doctors/" + rs.getObject("id", UUID.class) + "/photo"), serviceId);
    }
    @GetMapping("/doctors/{id}/photo")
    public ResponseEntity<Resource> photo(@org.springframework.web.bind.annotation.PathVariable UUID id) {
        String key = jdbc.query("select photo_storage_key from doctors where id=? and active=true", (rs, i) -> rs.getString(1), id).stream().findFirst().orElse(null);
        if (key == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(photos.load(key));
    }
}
