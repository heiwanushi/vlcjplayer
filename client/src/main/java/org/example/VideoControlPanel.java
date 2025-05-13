package org.example;

import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.TimeUnit;

public class VideoControlPanel extends JPanel {
    private final MediaPlayer mediaPlayer;
    private final JButton playPauseButton;
    private final JButton stopButton;
    private final JSlider progressBar;
    private final JSlider volumeSlider;
    private final JLabel currentTimeLabel;
    private final JLabel totalTimeLabel;
    private final JLabel volumeLabel;
    private final Timer updateTimer;
    private boolean isUserInteracting = false;
    private final ImageIcon playIcon;
    private final ImageIcon pauseIcon;
    private final ImageIcon volumeHighIcon;
    private final ImageIcon volumeMediumIcon;
    private final ImageIcon volumeMuteIcon;
    private boolean isPlaying = false;
    private final JButton repeatButton;
    private final ImageIcon repeatOnIcon;
    private final ImageIcon repeatOffIcon;
    private boolean isRepeatEnabled = false;
    private static final long SEEK_TIME = 5000; // 5 секунд для перемотки
    private boolean isMediaLoaded = false; // Добавляем флаг загрузки медиа

    public VideoControlPanel(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;

        // Делаем панель фокусируемой
        setFocusable(true);

        setLayout(new BorderLayout(1, 1));
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));

        // Загружаем иконки
        playIcon = loadIcon("/icons/play.png", "▶");
        pauseIcon = loadIcon("/icons/pause.png", "⏸");
        ImageIcon stopIcon = loadIcon("/icons/stop.png", "⏹");
        volumeHighIcon = loadIcon("/icons/volume-high.png", "🔊");
        volumeMediumIcon = loadIcon("/icons/volume-medium.png", "🔉");
        volumeMuteIcon = loadIcon("/icons/volume-mute.png", "🔈");
        repeatOnIcon = loadIcon("/icons/repeat-on.png", "🔁");
        repeatOffIcon = loadIcon("/icons/repeat-off.png", "↩");

        // Создаем кнопку повтора
        repeatButton = createButton(repeatOffIcon, "Повтор выключен");

        // Инициализация компонентов
        playPauseButton = createButton(playIcon, "Воспроизвести/Пауза (Пробел)");
        stopButton = createButton(stopIcon, "Стоп");
        volumeLabel = new JLabel();
        updateVolumeIcon(100);

        progressBar = new JSlider(0, 100, 0);
        progressBar.setPreferredSize(new Dimension(0, 20));
        progressBar.setToolTipText("Используйте стрелки ← и → для перемотки");

        volumeSlider = new JSlider(0, 100, 100);
        volumeSlider.setPreferredSize(new Dimension(100, 20));

        currentTimeLabel = new JLabel("00:00:00");
        totalTimeLabel = new JLabel("00:00:00");

        setupLayout();
        setupListeners();
        setControlsEnabled(false);

        updateTimer = new Timer(1000, e -> updateProgress());
        updateTimer.start();

        // Запрашиваем фокус при создании
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    /**
     * Активирует панель управления при загрузке медиа.
     */
    public void onMediaLoaded() {
        SwingUtilities.invokeLater(() -> {
            setControlsEnabled(true);
            updatePlayPauseButton(true); // Автоматически начинаем воспроизведение
        });
    }

    /**
     * Устанавливает доступность элементов управления.
     * @param enabled true для включения, false для отключения
     */
    private void setControlsEnabled(boolean enabled) {
        playPauseButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        repeatButton.setEnabled(enabled);
        progressBar.setEnabled(enabled);
        volumeSlider.setEnabled(enabled);
        isMediaLoaded = enabled;

        // Обновляем внешний вид
        if (!enabled) {
            currentTimeLabel.setText("00:00:00");
            totalTimeLabel.setText("00:00:00");
            progressBar.setValue(0);
            isPlaying = false;
            updatePlayPauseButton(false);
        }
    }

    private ImageIcon loadIcon(String path, String fallbackText) {
        try {
            java.net.URL iconUrl = getClass().getResource(path);
            if (iconUrl != null) {
                return new ImageIcon(iconUrl);
            }
        } catch (Exception e) {
            System.err.println("Ошибка загрузки иконки " + path + ": " + e.getMessage());
        }
        return null;
    }

    private JButton createButton(ImageIcon icon, String tooltip) {
        JButton button = new JButton();
        if (icon != null) {
            button.setIcon(icon);
        } else {
            // Если иконка не загрузилась, используем текст
            button.setText(tooltip.substring(0, 1));
            button.setFont(new Font("Arial", Font.PLAIN, 16));
        }
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(40, 40));
        return button;
    }

    private void setupLayout() {
        // Панель с кнопками управления
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        buttonPanel.add(playPauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(repeatButton);

        // Панель времени
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 0));
        timePanel.add(currentTimeLabel);
        timePanel.add(new JLabel("/"));
        timePanel.add(totalTimeLabel);

        // Панель громкости
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);

        // Верхняя панель с элементами управления
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonPanel, BorderLayout.WEST);
        topPanel.add(timePanel, BorderLayout.CENTER);
        topPanel.add(volumePanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
    }

    private void setupListeners() {
        // Добавляем обработчик клавиш
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE:
                        togglePlayPause();
                        e.consume();
                        break;
                    case KeyEvent.VK_LEFT:
                        seekBackward();
                        e.consume();
                        break;
                    case KeyEvent.VK_RIGHT:
                        seekForward();
                        e.consume();
                        break;
                }
            }
        });

        // Добавляем слушатель для кнопки повтора
        repeatButton.addActionListener(e -> toggleRepeat());


        // Добавляем слушатель фокуса для возврата фокуса на панель
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Запрашиваем фокус обратно через небольшую задержку
                SwingUtilities.invokeLater(() -> requestFocusInWindow());
            }
        });

        // Добавляем слушатель мыши для возврата фокуса при клике
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        // Кнопка Play/Pause
        playPauseButton.addActionListener(e -> {
            togglePlayPause();
            requestFocusInWindow(); // Возвращаем фокус панели после нажатия кнопки
        });

        // Кнопка Stop
        stopButton.addActionListener(e -> {
            mediaPlayer.controls().stop();
            progressBar.setValue(0);
            currentTimeLabel.setText("00:00:00");
            updatePlayPauseButton(false);
            requestFocusInWindow();
        });

        // Обновляем слушатель слайдера громкости
        volumeSlider.addChangeListener(e -> {
            int volume = volumeSlider.getValue();
            if (!volumeSlider.getValueIsAdjusting()) {
                mediaPlayer.audio().setVolume(volume);
            }
            updateVolumeIcon(volume);
        });

        // Слайдер прогресса
        progressBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isUserInteracting = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                seekToPosition(e);
                isUserInteracting = false;
            }
        });

        progressBar.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                seekToPosition(e);
            }
        });

        // Слайдер громкости
        volumeSlider.addChangeListener(e -> {
            if (!volumeSlider.getValueIsAdjusting()) {
                mediaPlayer.audio().setVolume(volumeSlider.getValue());
            }
        });


        // Добавляем слушатель событий плеера
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    updatePlayPauseButton(true);
                    if (!isMediaLoaded) {
                        setControlsEnabled(true);
                    }
                });
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> updatePlayPauseButton(false));
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    updatePlayPauseButton(false);
                    if (!mediaPlayer.media().isValid()) {
                        setControlsEnabled(false);
                    }
                });
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    if (isRepeatEnabled) {
                        mediaPlayer.controls().setPosition(0);
                        mediaPlayer.controls().play();
                    } else {
                        updatePlayPauseButton(false);
                    }
                });
            }
            @Override
            public void error(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> setControlsEnabled(false));
            }
        });
    }

    private void toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled;
        updateRepeatButton();
        requestFocusInWindow(); // Возвращаем фокус панели
    }

    private void updateRepeatButton() {
        if (isRepeatEnabled) {
            repeatButton.setIcon(repeatOnIcon != null ? repeatOnIcon : new JLabel("🔁").getIcon());
            repeatButton.setToolTipText("Повтор включен");
        } else {
            repeatButton.setIcon(repeatOffIcon != null ? repeatOffIcon : new JLabel("↩").getIcon());
            repeatButton.setToolTipText("Повтор выключен");
        }
    }

    private void seekForward() {
        if (mediaPlayer.status().isPlayable()) {
            long currentTime = mediaPlayer.status().time();
            long newTime = currentTime + SEEK_TIME;
            long duration = mediaPlayer.status().length();

            if (newTime < duration) {
                mediaPlayer.controls().setTime(newTime);
                updateProgressDisplay(newTime, duration);
            }
        }
    }

    private void updateProgressDisplay(long current, long total) {
        if (total > 0) {
            int percentage = (int) ((current * 100.0) / total);
            progressBar.setValue(percentage);
            currentTimeLabel.setText(formatTime(current));
        }
    }

    private void seekBackward() {
        if (mediaPlayer.status().isPlayable()) {
            long currentTime = mediaPlayer.status().time();
            long newTime = currentTime - SEEK_TIME;

            if (newTime < 0) {
                newTime = 0;
            }

            mediaPlayer.controls().setTime(newTime);
            updateProgressDisplay(newTime, mediaPlayer.status().length());
        }
    }

    private void togglePlayPause() {
        if (isPlaying) {
            mediaPlayer.controls().pause();
        } else {
            mediaPlayer.controls().play();
        }
        isPlaying = !isPlaying;
        updatePlayPauseButton(isPlaying);
    }

    private void updatePlayPauseButton(boolean playing) {
        isPlaying = playing;
        playPauseButton.setIcon(playing ? pauseIcon : playIcon);
        playPauseButton.setToolTipText(playing ? "Пауза" : "Воспроизвести");
    }

    private void seekToPosition(MouseEvent e) {
        int mouseX = e.getX();
        int width = progressBar.getWidth();
        float percentage = (float) mouseX / width;
        long duration = mediaPlayer.status().length();
        long newPosition = (long) (duration * percentage);
        mediaPlayer.controls().setTime(newPosition);
    }

    private void updateProgress() {
        if (!isUserInteracting && mediaPlayer.status().isPlaying()) {
            long current = mediaPlayer.status().time();
            long total = mediaPlayer.status().length();

            if (total > 0) {
                int percentage = (int) ((current * 100.0) / total);
                progressBar.setValue(percentage);

                currentTimeLabel.setText(formatTime(current));
                totalTimeLabel.setText(formatTime(total));
            }
        }
    }

    private void updateVolumeIcon(int volume) {
        Icon icon;
        if (volume == 0) {
            icon = volumeMuteIcon != null ? volumeMuteIcon : new JLabel("🔈").getIcon();
        } else if (volume < 50) {
            icon = volumeMediumIcon != null ? volumeMediumIcon : new JLabel("🔉").getIcon();
        } else {
            icon = volumeHighIcon != null ? volumeHighIcon : new JLabel("🔊").getIcon();
        }

        if (icon != null) {
            volumeLabel.setIcon(icon);
        } else {
            volumeLabel.setText(volume == 0 ? "🔈" : volume < 50 ? "🔉" : "🔊");
            volumeLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        }
    }

    private String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (isMediaLoaded) {
            SwingUtilities.invokeLater(this::requestFocusInWindow);
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Point p = e.getPoint();
        if (progressBar.getBounds().contains(p)) {
            return "Используйте стрелки ← и → для перемотки видео на 5 секунд";
        }
        return super.getToolTipText(e);
    }

    public void release() {
        updateTimer.stop();
    }
}