package org.example;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public class VideoClient extends JFrame {
    private final VideoPlayerPanel playerPanel;
    private final VideoListPanel listPanel;
    private final NetworkManager networkManager;

    public VideoClient() {
        super("Видеоплеер");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null); // Центрируем окно

        // Инициализация компонентов
        networkManager = new NetworkManager();
        playerPanel = new VideoPlayerPanel() {
            @Override
            protected void onVideoStarted(Path videoPath, boolean isLocal) {
                // Обновляем заголовок окна при начале воспроизведения
                String source = isLocal ? "Локальное" : "Сервер";
                String title = String.format("Видеоплеер - %s (%s)",
                        videoPath.getFileName().toString(), source);
                setTitle(title);
            }
        };
        listPanel = new VideoListPanel(networkManager, playerPanel);

        // Настройка интерфейса
        JPanel mainPanel = new JPanel(new BorderLayout(1, 1));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        mainPanel.add(playerPanel, BorderLayout.CENTER);
        mainPanel.add(listPanel, BorderLayout.EAST);

        add(mainPanel);

        // Добавляем слушатель закрытия окна
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    @Override
    public void dispose() {
        networkManager.shutdown();
        playerPanel.release();
        super.dispose();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            System.err.println("Ошибка установки темы оформления: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            VideoClient client = new VideoClient();
            client.setVisible(true);
        });
    }
}