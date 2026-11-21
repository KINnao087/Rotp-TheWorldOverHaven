package com.inza.twoh.command.agent.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.inza.twoh.command.agent.entitiy.ProfileCatalog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * EN: Commit a whole document or keep its predecessor. Never truncate the live file in place.
 * 中文：整份文件提交或完全保留旧版，不在原文件上截断重写。
 */
public final class ModelConfigPersister {
    public void save(Path directory, ProfileCatalog catalog) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("version", 1);
        data.addProperty("active", catalog.active);
        data.add("profiles", new GsonBuilder().create().toJsonTree(catalog.profiles.values()));
        writeAtomic(directory.resolve(ConfigText.FILE), new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }

    static void writeAtomic(Path file, String contents) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), ".twoh-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(contents);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
