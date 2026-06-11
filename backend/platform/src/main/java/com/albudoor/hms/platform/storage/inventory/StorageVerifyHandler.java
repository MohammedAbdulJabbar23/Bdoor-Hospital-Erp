package com.albudoor.hms.platform.storage.inventory;

import com.albudoor.hms.platform.exception.StorageMissingException;
import com.albudoor.hms.platform.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class StorageVerifyHandler {

    private static final Logger log = LoggerFactory.getLogger(StorageVerifyHandler.class);

    private final List<DocumentInventoryContributor> contributors;
    private final FileStorage storage;
    private final Path root;

    public StorageVerifyHandler(List<DocumentInventoryContributor> contributors, FileStorage storage,
                                @Value("${hms.attachments.dir:data/attachments}") String dir) {
        this.contributors = contributors;
        this.storage = storage;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public StorageVerifyResponse verify() {
        List<DocumentRef> missing = new ArrayList<>();
        List<DocumentRef> corrupt = new ArrayList<>();
        List<DocumentRef> unreadable = new ArrayList<>();
        Set<String> referenced = new HashSet<>();
        int checked = 0;
        for (DocumentInventoryContributor c : contributors) {
            List<DocumentRef> refs;
            try {
                refs = c.documentRefs();
            } catch (RuntimeException e) {
                log.error("Storage verify: contributor {} failed to enumerate document refs; skipping it",
                        c.getClass().getName(), e);
                continue;
            }
            for (DocumentRef ref : refs) {
                checked++;
                if (ref.storageKey() == null) {
                    unreadable.add(ref);
                    continue;
                }
                referenced.add(ref.storageKey());
                try (InputStream in = storage.open(ref.storageKey())) {
                    if (ref.sha256() != null) {
                        MessageDigest md;
                        try {
                            md = MessageDigest.getInstance("SHA-256");
                        } catch (java.security.NoSuchAlgorithmException e) {
                            throw new IllegalStateException(e);
                        }
                        try (var din = new DigestInputStream(in, md)) {
                            din.transferTo(OutputStream.nullOutputStream());
                        }
                        if (!HexFormat.of().formatHex(md.digest()).equals(ref.sha256())) corrupt.add(ref);
                    }
                } catch (StorageMissingException e) {
                    missing.add(ref);
                } catch (IOException | UncheckedIOException e) {
                    log.error("Storage verify: failed to read {} ({}/{})", ref.storageKey(), ref.owner(), ref.refId(), e);
                    unreadable.add(ref);
                }
            }
        }
        List<String> orphaned = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .map(p -> root.relativize(p).toString().replace('\\', '/'))
                        .filter(k -> !referenced.contains(k))
                        .forEach(orphaned::add);
            } catch (IOException | UncheckedIOException e) {
                log.error("Storage verify: orphan scan of {} failed; orphanedFiles list may be incomplete", root, e);
            }
        }
        return new StorageVerifyResponse(checked, missing, corrupt, unreadable, orphaned);
    }
}
