package org.example;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class FileUtils {
    public static void cleanupOldFiles(Path directory, long maxAge, TimeUnit unit) {
        try {
            if (!Files.exists(directory)) {
                return;
            }

            long cutoff = System.currentTimeMillis() - unit.toMillis(maxAge);

            Files.walk(directory)
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoff;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Ошибка удаления файла: " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Ошибка при очистке старых файлов: " + e.getMessage());
        }
    }
}