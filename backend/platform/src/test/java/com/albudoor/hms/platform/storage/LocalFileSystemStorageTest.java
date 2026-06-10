package com.albudoor.hms.platform.storage;

import com.albudoor.hms.platform.exception.StorageMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileSystemStorageTest {

    @TempDir Path dir;

    private LocalFileSystemStorage storage() throws IOException {
        return new LocalFileSystemStorage(dir.toString());
    }

    @Test
    void saveVerified_records_sha256_and_size_and_roundtrips() throws IOException {
        var s = storage();
        byte[] payload = "hello documents".getBytes(StandardCharsets.UTF_8);
        StoredBlob blob = s.saveVerified(new ByteArrayInputStream(payload), "report.pdf");
        // sha256 of "hello documents"
        assertThat(blob.sha256()).isEqualTo("7cfb967ffc81da5f93b59438dd55c9c535edd6e6c997eacdc6d263abdf57d287");
        assertThat(blob.sizeBytes()).isEqualTo(payload.length);
        assertThat(blob.storageKey()).endsWith(".pdf");
        try (var in = s.open(blob.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    void open_missing_key_throws_typed_exception() throws IOException {
        var s = storage();
        assertThatThrownBy(() -> s.open("2026-06-10/does-not-exist.pdf"))
                .isInstanceOf(StorageMissingException.class);
    }
}
