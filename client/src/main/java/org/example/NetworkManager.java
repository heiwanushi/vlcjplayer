package org.example;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkManager {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    private static final String TEMP_DIR = "temp";
    private static final int CONNECTION_TIMEOUT = 5000; // 5 секунд

    private final ExecutorService executorService;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final Object connectionLock = new Object();

    public NetworkManager() {
        this.executorService = Executors.newCachedThreadPool();
        createTempDirectory();
        connect(() -> {}, e -> System.err.println("Initial connection failed: " + e.getMessage()));
    }

    private void createTempDirectory() {
        try {
            Path tempDir = Paths.get(TEMP_DIR);
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка создания временной директории", e);
        }
    }

    public boolean isConnected() {
        return isConnected.get() && socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void connect(Runnable onSuccess, Consumer<Exception> onError) {
        synchronized (connectionLock) {
            if (isConnected()) {
                onSuccess.run();
                return;
            }

            executorService.submit(() -> {
                try {
                    closeConnection();

                    socket = new Socket(SERVER_HOST, SERVER_PORT);
                    socket.setSoTimeout(CONNECTION_TIMEOUT);

                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());

                    isConnected.set(true);
                    System.out.println("Successfully connected to server");
                    onSuccess.run();
                } catch (IOException e) {
                    System.err.println("Connection failed: " + e.getMessage());
                    isConnected.set(false);
                    onError.accept(e);
                }
            });
        }
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        } finally {
            out = null;
            in = null;
            socket = null;
            isConnected.set(false);
        }
    }

    public void requestVideoList(Consumer<List<String>> onSuccess, Consumer<Exception> onError) {
        if (!isConnected()) {
            connect(() -> doRequestVideoList(onSuccess, onError), onError);
            return;
        }

        doRequestVideoList(onSuccess, onError);
    }

    private void doRequestVideoList(Consumer<List<String>> onSuccess, Consumer<Exception> onError) {
        executorService.submit(() -> {
            try {
                System.out.println("Отправка команды LIST на сервер...");
                synchronized (connectionLock) {
                    out.writeObject("LIST");
                    out.flush();

                    @SuppressWarnings("unchecked")
                    List<String> videos = (List<String>) in.readObject();
                    System.out.println("Получен ответ от сервера: " + videos);

                    onSuccess.accept(videos);
                }
            } catch (Exception e) {
                System.err.println("Ошибка при запросе списка видео: " + e.getMessage());
                handleConnectionError(e);
                onError.accept(e);
            }
        });
    }

    /**
     * Проверяет наличие и актуальность локального файла.
     */
    public void checkLocalVideo(String videoName, Consumer<VideoFileInfo> onResult, Consumer<Exception> onError) {
        executorService.submit(() -> {
            try {
                Path localFile = Paths.get(TEMP_DIR, videoName);
                if (!Files.exists(localFile)) {
                    onResult.accept(new VideoFileInfo(false, null));
                    return;
                }

                if (!isConnected()) {
                    // Если нет подключения, считаем локальный файл актуальным
                    onResult.accept(new VideoFileInfo(true, localFile));
                    return;
                }

                // Проверяем актуальность на сервере
                synchronized (connectionLock) {
                    out.writeObject("CHECK " + videoName);
                    out.flush();

                    long serverFileSize = in.readLong();
                    long serverModifiedTime = in.readLong();

                    if (serverFileSize == -1) {
                        // Файл не существует на сервере
                        Files.deleteIfExists(localFile);
                        onResult.accept(new VideoFileInfo(false, null));
                        return;
                    }

                    long localFileSize = Files.size(localFile);
                    long localModifiedTime = Files.getLastModifiedTime(localFile).toMillis();

                    boolean isActual = localFileSize == serverFileSize &&
                            localModifiedTime >= serverModifiedTime;

                    onResult.accept(new VideoFileInfo(isActual, isActual ? localFile : null));
                }
            } catch (Exception e) {
                System.err.println("Ошибка при проверке локального файла: " + e.getMessage());
                // При ошибке проверки считаем локальный файл актуальным, если он существует
                try {
                    Path localFile = Paths.get(TEMP_DIR, videoName);
                    onResult.accept(new VideoFileInfo(Files.exists(localFile),
                            Files.exists(localFile) ? localFile : null));
                } catch (Exception ex) {
                    onError.accept(ex);
                }
            }
        });
    }

    public void downloadVideo(String videoName,
                              Consumer<Integer> onProgress,
                              Consumer<Path> onSuccess,
                              Consumer<Exception> onError) {
        // Сначала проверяем локальный файл
        checkLocalVideo(videoName,
                fileInfo -> {
                    if (fileInfo.isActual() && fileInfo.getPath() != null) {
                        // Если файл актуален, сразу возвращаем его
                        onSuccess.accept(fileInfo.getPath());
                    } else {
                        // Иначе загружаем с сервера
                        if (!isConnected()) {
                            connect(() -> doDownloadVideo(videoName, onProgress, onSuccess, onError), onError);
                        } else {
                            doDownloadVideo(videoName, onProgress, onSuccess, onError);
                        }
                    }
                },
                error -> {
                    // При ошибке проверки пытаемся загрузить с сервера
                    if (!isConnected()) {
                        connect(() -> doDownloadVideo(videoName, onProgress, onSuccess, onError), onError);
                    } else {
                        doDownloadVideo(videoName, onProgress, onSuccess, onError);
                    }
                }
        );
    }

    /**
     * Класс для хранения информации о локальном файле
     */
    public static class VideoFileInfo {
        private final boolean isActual;
        private final Path path;

        public VideoFileInfo(boolean isActual, Path path) {
            this.isActual = isActual;
            this.path = path;
        }

        public boolean isActual() {
            return isActual;
        }

        public Path getPath() {
            return path;
        }
    }

    private void doDownloadVideo(String videoName,
                                 Consumer<Integer> onProgress,
                                 Consumer<Path> onSuccess,
                                 Consumer<Exception> onError) {
        executorService.submit(() -> {
            try {
                synchronized (connectionLock) {
                    out.writeObject("GET " + videoName);
                    out.flush();

                    long fileSize = in.readLong();
                    if (fileSize == -1) {
                        throw new IOException("Видео не найдено");
                    }

                    Path tempFile = Paths.get(TEMP_DIR, videoName);
                    long totalBytesRead = 0;

                    try (OutputStream fileOut = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while (totalBytesRead < fileSize &&
                                (count = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) > 0) {
                            fileOut.write(buffer, 0, count);
                            totalBytesRead += count;
                            onProgress.accept((int) ((totalBytesRead * 100) / fileSize));
                        }
                    }

                    onSuccess.accept(tempFile);
                }
            } catch (Exception e) {
                handleConnectionError(e);
                onError.accept(e);
            }
        });
    }

    private void handleConnectionError(Exception e) {
        if (e instanceof SocketException || e instanceof EOFException) {
            System.err.println("Connection lost: " + e.getMessage());
            isConnected.set(false);
            closeConnection();
        }
    }

    public void shutdown() {
        executorService.shutdown();
        closeConnection();
    }
}