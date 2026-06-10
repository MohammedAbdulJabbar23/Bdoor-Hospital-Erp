package com.albudoor.hms.platform.storage.inventory;

import com.albudoor.hms.platform.exception.StorageMissingException;
import com.albudoor.hms.platform.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    private final List<DocumentInventoryContributor> contributors;
    private final FileStorage storage;
    private final Path root;

    public StorageVerifyHandler(List<DocumentInventoryContributor> contributors, FileStorage storage,
                                @Value("${hms.attachments.dir:data/attachments}") String dir) {
        this.contributors = contributors;
        this.storage = storage;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public StorageVerifyResponse verify() throws IOException {
        List<DocumentRef> missing = new ArrayList<>();
        List<DocumentRef> corrupt = new ArrayList<>();
        Set<String> referenced = new HashSet<>();
        int checked = 0;
        for (DocumentInventoryContributor c : contributors) {
            for (DocumentRef ref : c.documentRefs()) {
                checked++;
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
            }
        }
        return new StorageVerifyResponse(checked, missing, corrupt, orphaned);
    }
}
