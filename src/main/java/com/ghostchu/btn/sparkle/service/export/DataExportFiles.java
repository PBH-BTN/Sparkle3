package com.ghostchu.btn.sparkle.service.export;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;

@Component
public class DataExportFiles {
    private final Path directory;

    public DataExportFiles(@Value("${sparkle.exports.directory:data/exports}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public Path directory() {
        return directory;
    }

    public Path ready(UUID id) {
        return directory.resolve(id + ".csv.xz");
    }

    Path part(UUID id) {
        return directory.resolve(id + ".part");
    }
}
