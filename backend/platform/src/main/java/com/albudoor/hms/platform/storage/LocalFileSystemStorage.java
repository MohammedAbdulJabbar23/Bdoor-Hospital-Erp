package com.albudoor.hms.platform.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local-filesystem implementation of {@link FileStorage}. Files are laid out as
 * {@code <root>/<yyyy-MM-dd>/<uuid>.<ext>} so a single directory never gets too crowded
 * and operators can ls a day at a time.
 *
 * <p>Configure with {@code hms.attachments.dir}; defaults to {@code data/attachments}
 * relative to the working directory.
 */
@Component
public class LocalFileSystemStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorage.class);

    private final Path root;

    public LocalFileSystemStorage(@Value("${hms.attachments.dir:data/attachments}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        log.info("FileStorage rooted at {}", this.root);
    }

    @Override
    public String save(InputStream in, String suggestedName, long sizeBytes) throws IOException {
        String ext = extOf(suggestedName);
        String key = LocalDate.now() + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = root.resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        Path p = root.resolve(storageKey).normalize();
        // Path-traversal guard
        if (!p.startsWith(root)) {
            throw new IOException("Refusing to open path outside storage root: " + storageKey);
        }
        return Files.newInputStream(p);
    }

    @Override
    public boolean delete(String storageKey) throws IOException {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) {
            throw new IOException("Refusing to delete path outside storage root: " + storageKey);
        }
        return Files.deleteIfExists(p);
    }

    private static String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase();
        // Allow only sane extensions; everything else falls back to no-ext.
        return ext.matches("[a-z0-9]{1,8}") ? ext : "";
    }
}
