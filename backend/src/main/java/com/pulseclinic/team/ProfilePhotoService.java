package com.pulseclinic.team;

import com.pulseclinic.auth.AuthException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@Service
public class ProfilePhotoService {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path root;
    public ProfilePhotoService(@Value("${PULSECLINIC_UPLOAD_DIR:/app/uploads}") String uploadDirectory) {
        this.root = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }
    String save(UUID userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES || !TYPES.contains(file.getContentType())) {
            throw new AuthException("INVALID_PROFILE_PHOTO", "Choose a JPG, PNG, or WebP image up to 5 MB.");
        }
        String extension = switch (file.getContentType()) { case "image/jpeg" -> "jpg"; case "image/png" -> "png"; default -> "webp"; };
        String key = "team-" + userId + "." + extension;
        try {
            Files.createDirectories(root);
            try (var input = file.getInputStream()) { Files.copy(input, root.resolve(key), StandardCopyOption.REPLACE_EXISTING); }
            return key;
        } catch (IOException ex) {
            throw new IllegalStateException("Profile photo storage is unavailable.", ex);
        }
    }
    public Resource load(String key) {
        try {
            Path file = root.resolve(key).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new AuthException("PROFILE_PHOTO_NOT_FOUND", "Profile photo not found.");
            return new UrlResource(file.toUri());
        } catch (IOException ex) { throw new AuthException("PROFILE_PHOTO_NOT_FOUND", "Profile photo not found."); }
    }
    void delete(String key) {
        try {
            Path file = root.resolve(key).normalize();
            if (file.startsWith(root)) Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Profile photo storage is unavailable.", ex);
        }
    }
}
