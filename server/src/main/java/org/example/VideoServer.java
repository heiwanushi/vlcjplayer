package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс VideoServer реализует видеосервер, который позволяет клиентам
 * подключаться и запрашивать список видео или загружать конкретные видеофайлы.
 */
public class VideoServer {
    private static final int PORT = 8080; // Порт, на котором будет работать сервер
    private static final String VIDEO_DIR = "videos"; // Директория с видеофайлами
    private final List<String> videoList; // Список доступных видео
    private boolean running; // Флаг работы сервера
    private final AtomicInteger clientCounter = new AtomicInteger(0); // Счетчик клиентов

    /**
     * Конструктор VideoServer.
     * Инициализирует видеосервер, загружает список видео.
     */
    public VideoServer() {
        this.videoList = new ArrayList<>();
        this.running = true;
        loadVideos(); // Загрузка списка видео при запуске
        startWatchingVideoDirectory(); // Запуск мониторинга изменений
    }

    /**
     * Загружает список видео из директории VIDEO_DIR.
     */
    private void loadVideos() {
        try {
            Path videoPath = Paths.get(VIDEO_DIR);
            if (!Files.exists(videoPath)) {
                Files.createDirectories(videoPath); // Создаем директорию, если она не существует
            }

            videoList.clear(); // Очищаем старый список
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(videoPath, "*.{mp4,MP4}")) {
                for (Path file : stream) {
                    videoList.add(file.getFileName().toString()); // Добавляем файлы в список
                }
            }

            System.out.println("Загруженные видео: " + videoList);
        } catch (IOException e) {
            System.err.println("Ошибка при загрузке видео: " + e.getMessage());
        }
    }

    /**
     * Запускает мониторинг изменений в директории VIDEO_DIR.
     */
    private void startWatchingVideoDirectory() {
        new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path videoPath = Paths.get(VIDEO_DIR);
                videoPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

                System.out.println("Мониторинг изменений в директории: " + VIDEO_DIR);

                while (running) {
                    WatchKey key = watchService.take(); // Ожидание события
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        // Обновляем список видео при любых изменениях
                        System.out.println("Изменения в директории: " + event.kind() + " - " + event.context());
                        loadVideos();
                    }
                    key.reset(); // Сбрасываем ключ для получения следующих событий
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Ошибка при мониторинге директории: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Запускает видеосервер и обрабатывает подключения от клиентов.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Ожидание клиента
                    int clientId = clientCounter.incrementAndGet(); // Генерация уникального идентификатора клиента
                    new ClientHandler(clientSocket, clientId).start(); // Обработка клиента в отдельном потоке
                    System.out.println("Новое подключение [Клиент " + clientId + "]: " + clientSocket.getInetAddress());
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Ошибка при принятии подключения: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }

    /**
     * Вложенный класс для обработки запросов клиентов.
     */
    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final int clientId; // Идентификатор клиента
        private ObjectOutputStream out;
        private ObjectInputStream in;

        /**
         * Конструктор ClientHandler.
         *
         * @param socket сокет клиента
         * @param clientId уникальный идентификатор клиента
         */
        public ClientHandler(Socket socket, int clientId) {
            this.clientSocket = socket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try {
                // Инициализация потоков ввода/вывода
                out = new ObjectOutputStream(clientSocket.getOutputStream());
                in = new ObjectInputStream(clientSocket.getInputStream());

                while (!clientSocket.isClosed()) {
                    String command = (String) in.readObject(); // Чтение команды от клиента
                    processCommand(command); // Обработка команды
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Клиент " + clientId + " отключился: " + clientSocket.getInetAddress());
            } finally {
                closeConnection(); // Закрытие соединения
            }
        }

        /**
         * Обрабатывает команду клиента.
         *
         * @param command команда клиента
         * @throws IOException если возникает ошибка при обработке команды
         */
        private void processCommand(String command) throws IOException {
            System.out.println("[Клиент " + clientId + "] Получена команда: " + command);

            if ("LIST".equals(command)) {
                System.out.println("[Клиент " + clientId + "] Отправляем список видео клиенту.");
                synchronized (videoList) {
                    out.writeObject(new ArrayList<>(videoList));
                    out.flush();
                }
            } else if (command.startsWith("GET ")) {
                String videoName = command.substring(4);
                System.out.println("[Клиент " + clientId + "] Запрос на получение видео: " + videoName);
                sendVideo(videoName);
            } else if (command.startsWith("CHECK ")) {
                String videoName = command.substring(6);
                System.out.println("[Клиент " + clientId + "] Запрос на проверку видео: " + videoName);
                checkVideo(videoName);
            }
        }

        /**
         * Проверяет видеофайл и отправляет информацию о нем клиенту.
         *
         * @param videoName имя видеофайла
         * @throws IOException если возникает ошибка при проверке файла
         */
        private void checkVideo(String videoName) throws IOException {
            Path videoPath = Paths.get(VIDEO_DIR, videoName);
            if (Files.exists(videoPath)) {
                try {
                    long fileSize = Files.size(videoPath);
                    long lastModified = Files.getLastModifiedTime(videoPath).toMillis();

                    out.writeLong(fileSize);
                    out.writeLong(lastModified);
                    out.flush();

                    System.out.println("[Клиент " + clientId + "] Отправлена информация о файле: " + videoName +
                            " (размер: " + fileSize + ", модифицирован: " + lastModified + ")");
                } catch (IOException e) {
                    System.err.println("[Клиент " + clientId + "] Ошибка при проверке видео: " + e.getMessage());
                    out.writeLong(-1);
                    out.writeLong(-1);
                    out.flush();
                }
            } else {
                System.out.println("[Клиент " + clientId + "] Файл не найден: " + videoName);
                out.writeLong(-1);
                out.writeLong(-1);
                out.flush();
            }
        }

        /**
         * Отправляет видеофайл клиенту.
         *
         * @param videoName имя видеофайла
         * @throws IOException если возникает ошибка при отправке файла
         */
        private void sendVideo(String videoName) throws IOException {
            Path videoPath = Paths.get(VIDEO_DIR, videoName);
            if (Files.exists(videoPath)) {
                try {
                    long fileSize = Files.size(videoPath);
                    out.writeLong(fileSize);
                    out.flush();

                    // Отправляем содержимое файла
                    try (InputStream fileIn = Files.newInputStream(videoPath)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = fileIn.read(buffer)) > 0) {
                            out.write(buffer, 0, count);
                        }
                    }
                    out.flush();
                    System.out.println("[Клиент " + clientId + "] Видео " + videoName + " успешно отправлено.");
                } catch (IOException e) {
                    System.err.println("[Клиент " + clientId + "] Ошибка при отправке видео: " + e.getMessage());
                    out.writeLong(-1);
                    out.flush();
                }
            } else {
                System.out.println("[Клиент " + clientId + "] Файл не найден: " + videoName);
                out.writeLong(-1);
                out.flush();
            }
        }

        /**
         * Закрывает соединение с клиентом.
         */
        private void closeConnection() {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                System.err.println("[Клиент " + clientId + "] Ошибка при закрытии соединения: " + e.getMessage());
            }
        }
    }

    /**
     * Останавливает сервер.
     */
    public void stop() {
        running = false;
    }

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        VideoServer server = new VideoServer();
        server.start();
    }
}