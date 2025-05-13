package org.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Панель со списком доступных видео и элементами управления.
 */
public class VideoListPanel extends JPanel {
    private final JList<String> videoList;
    private final DefaultListModel<String> listModel;
    private final JProgressBar progressBar;
    private final NetworkManager networkManager;
    private final VideoPlayerPanel playerPanel;
    private final JButton refreshButton;
    private boolean isLoading = false;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Создает панель списка видео.
     *
     * @param networkManager менеджер сетевого взаимодействия
     * @param playerPanel панель воспроизведения видео
     */
    public VideoListPanel(NetworkManager networkManager, VideoPlayerPanel playerPanel) {
        this.networkManager = networkManager;
        this.playerPanel = playerPanel;

        setLayout(new BorderLayout(5, 5));
        setPreferredSize(new Dimension(250, 0));
        setBorder(new EmptyBorder(0, 5, 5, 5));

        // Инициализация компонентов
        listModel = new DefaultListModel<>();
        videoList = new JList<>(listModel);
        videoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        refreshButton = new JButton("Обновить список");
        refreshButton.setFocusPainted(false);

        // Настройка интерфейса
        setupUI();
        setupListeners();

        // Загрузка списка видео
        refreshVideoList();
    }

    /**
     * Настраивает пользовательский интерфейс панели.
     */
    private void setupUI() {
        // Заголовок
        add(new JLabel("Доступные видео"), BorderLayout.NORTH);

        // Список видео
        JScrollPane scrollPane = new JScrollPane(videoList);
        add(scrollPane, BorderLayout.CENTER);

        // Панель управления
        JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
        controlPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        // Добавляем обработчик нажатия на кнопку
        refreshButton.addActionListener(e -> refreshVideoList());

        controlPanel.add(refreshButton, BorderLayout.NORTH);
        controlPanel.add(progressBar, BorderLayout.SOUTH);

        add(controlPanel, BorderLayout.SOUTH);
    }

    /**
     * Настраивает слушателей событий.
     */
    private void setupListeners() {
        // Обновляем обработчик кликов по списку
        videoList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isLoading) return;

                int index = videoList.locationToIndex(e.getPoint());
                if (index != -1) {
                    Rectangle bounds = videoList.getCellBounds(index, index);
                    if (bounds != null && bounds.contains(e.getPoint())) {
                        String selectedVideo = videoList.getModel().getElementAt(index);
                        verifyAndLoadVideo(selectedVideo);
                    }
                }
            }
        });
    }

    /**
     * Проверяет актуальность выбранного видео и загружает его.
     *
     * @param selectedVideo имя выбранного видеофайла
     */
    private void verifyAndLoadVideo(String selectedVideo) {
        setUIEnabled(false);
        isLoading = true;
        progressBar.setIndeterminate(true);
        progressBar.setString("Проверка локальной копии...");
        progressBar.setVisible(true);

        // Сначала проверяем локальную копию
        networkManager.checkLocalVideo(selectedVideo,
                fileInfo -> SwingUtilities.invokeLater(() -> {
                    if (fileInfo.isActual() && fileInfo.getPath() != null) {
                        // Используем локальную копию
                        System.out.println("Используется локальная копия: " + fileInfo.getPath());
                        playerPanel.playVideo(fileInfo.getPath());
                        setUIEnabled(true);
                        isLoading = false;
                        progressBar.setVisible(false);
                    } else {
                        // Проверяем наличие на сервере и загружаем
                        verifyOnServerAndLoad(selectedVideo);
                    }
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    System.err.println("Ошибка проверки локального файла: " + error.getMessage());
                    // При ошибке проверки пытаемся загрузить с сервера
                    verifyOnServerAndLoad(selectedVideo);
                })
        );
    }

    /**
     * Проверяет наличие видео на сервере и загружает его.
     *
     * @param selectedVideo имя выбранного видеофайла
     */
    private void verifyOnServerAndLoad(String selectedVideo) {
        progressBar.setString("Проверка доступности на сервере...");

        networkManager.requestVideoList(
                videos -> SwingUtilities.invokeLater(() -> {
                    updateVideoList(videos);

                    if (videos != null && videos.contains(selectedVideo)) {
                        progressBar.setIndeterminate(false);
                        progressBar.setString("Загрузка видео...");
                        loadVideo(selectedVideo);
                    } else {
                        showError("Выбранное видео не доступно на сервере", null);
                        setUIEnabled(true);
                        isLoading = false;
                        progressBar.setVisible(false);
                    }
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    showError("Ошибка проверки доступности видео", error);
                    setUIEnabled(true);
                    isLoading = false;
                    progressBar.setVisible(false);
                })
        );
    }

    /**
     * Обновляет список видео в модели.
     *
     * @param videos новый список видео
     */
    private void updateVideoList(List<String> videos) {
        listModel.clear();
        if (videos != null && !videos.isEmpty()) {
            videos.forEach(listModel::addElement);
        }
        System.out.println("Список видео обновлен, количество: " + listModel.size());
    }

    /**
     * Обновляет список доступных видео с сервера.
     */
    private void refreshVideoList() {
        if (isLoading) {
            return;
        }

        setUIEnabled(false);
        isLoading = true;
        progressBar.setIndeterminate(true);
        progressBar.setString("Обновление списка видео...");
        progressBar.setVisible(true);

        networkManager.requestVideoList(
                videos -> SwingUtilities.invokeLater(() -> {
                    updateVideoList(videos);
                    setUIEnabled(true);
                    isLoading = false;
                    progressBar.setVisible(false);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    showError("Ошибка получения списка видео", error);
                    setUIEnabled(true);
                    isLoading = false;
                    progressBar.setVisible(false);
                })
        );
    }
    /**
     * Пытается подключиться к серверу и получить список видео.
     *
     * @param attemptsLeft количество оставшихся попыток
     */
    private void tryConnectAndGetList(int attemptsLeft) {
        if (attemptsLeft <= 0) {
            SwingUtilities.invokeLater(() -> {
                showError("Не удалось подключиться к серверу после нескольких попыток", null);
                setUIEnabled(true);
                isLoading = false;
                progressBar.setVisible(false);
            });
            return;
        }

        if (!networkManager.isConnected() && !isConnecting.get()) {
            isConnecting.set(true);

            networkManager.connect(
                    // Успешное подключение
                    () -> {
                        isConnecting.set(false);
                        requestVideoList(attemptsLeft);
                    },
                    // Ошибка подключения
                    error -> {
                        isConnecting.set(false);
                        System.err.println("Попытка подключения " + (MAX_RETRY_ATTEMPTS - attemptsLeft + 1) +
                                " не удалась: " + error.getMessage());
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setString("Повторная попытка подключения (" + attemptsLeft + ")...");
                            tryConnectAndGetList(attemptsLeft - 1);
                        });
                    }
            );
        } else {
            requestVideoList(attemptsLeft);
        }
    }

    /**
     * Запрашивает список видео с сервера.
     *
     * @param attemptsLeft количество оставшихся попыток
     */
    private void requestVideoList(int attemptsLeft) {
        SwingUtilities.invokeLater(() ->
                progressBar.setString("Получение списка видео..."));

        networkManager.requestVideoList(
                videos -> SwingUtilities.invokeLater(() -> {
                    System.out.println("Получен список видео: " + videos);
                    listModel.clear();
                    if (videos != null && !videos.isEmpty()) {
                        videos.forEach(video -> {
                            System.out.println("Добавление видео в список: " + video);
                            listModel.addElement(video);
                        });
                    } else {
                        System.out.println("Получен пустой список видео");
                    }
                    System.out.println("Текущий размер списка: " + listModel.size());
                    setUIEnabled(true);
                    isLoading = false;
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    System.err.println("Ошибка получения списка: " + error.getMessage());
                    if (attemptsLeft > 1) {
                        progressBar.setString("Повторная попытка получения списка...");
                        tryConnectAndGetList(attemptsLeft - 1);
                    } else {
                        showError("Не удалось получить список видео", error);
                        setUIEnabled(true);
                        isLoading = false;
                        progressBar.setVisible(false);
                        progressBar.setIndeterminate(false);
                    }
                })
        );
    }

    /**
     * Загружает выбранное видео.
     *
     * @param videoName имя видеофайла
     */
    private void loadVideo(String videoName) {
        networkManager.downloadVideo(videoName,
                // Обработчик прогресса
                progress -> SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(progress);
                    progressBar.setString("Загрузка: " + progress + "%");
                }),
                // Обработчик успешного завершения
                videoPath -> SwingUtilities.invokeLater(() -> {
                    playerPanel.playVideo(videoPath);
                    setUIEnabled(true);
                    isLoading = false;
                    progressBar.setVisible(false);
                }),
                // Обработчик ошибки
                error -> SwingUtilities.invokeLater(() -> {
                    showError("Ошибка загрузки видео", error);
                    setUIEnabled(true);
                    isLoading = false;
                    progressBar.setVisible(false);
                })
        );
    }

    /**
     * Включает/выключает элементы управления панели.
     *
     * @param enabled true для включения, false для выключения
     */
    private void setUIEnabled(boolean enabled) {
        videoList.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        progressBar.setVisible(!enabled);
        if (enabled) {
            progressBar.setValue(0);
        }

        // Сбрасываем выделение при блокировке
        if (!enabled) {
            videoList.clearSelection();
        }
    }

    /**
     * Показывает сообщение об ошибке.
     *
     * @param message сообщение об ошибке
     * @param e исключение, вызвавшее ошибку
     */
    private void showError(String message, Exception e) {
        String fullMessage = e != null ? message + ": " + e.getMessage() : message;
        JOptionPane.showMessageDialog(this, fullMessage, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }
}