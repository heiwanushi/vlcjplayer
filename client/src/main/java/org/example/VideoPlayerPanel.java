package org.example;

import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class VideoPlayerPanel extends JPanel {
    private final EmbeddedMediaPlayerComponent mediaPlayer;
    private final VideoControlPanel controlPanel;
    private final DatabaseManager databaseManager;
    private String currentVideoName;

    // Поддерживаемые форматы видео
    private static final String[] SUPPORTED_FORMATS = {".mp4", ".avi", ".mkv", ".mov", ".flv"};

    public VideoPlayerPanel() {
        setLayout(new BorderLayout());

        // Инициализация медиаплеера
        mediaPlayer = new EmbeddedMediaPlayerComponent();
        add(mediaPlayer, BorderLayout.CENTER);

        // Инициализация панели управления
        controlPanel = new VideoControlPanel(mediaPlayer.mediaPlayer());
        add(controlPanel, BorderLayout.SOUTH);

        // Инициализация базы данных
        databaseManager = new DatabaseManager();

        // Добавление поддержки Drag and Drop
        setupDragAndDrop();

        // Добавление обработчика события изменения позиции
        mediaPlayer.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void positionChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, float newPosition) {
                if (currentVideoName != null) {
                    long currentTime = mediaPlayer.status().time();
                    databaseManager.saveProgress(currentVideoName, currentTime);
                }
            }
        });
    }

    private void setupDragAndDrop() {
        new DropTarget(this, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        if (file.isFile() && isSupportedFormat(file)) {
                            playLocalVideo(file.toPath());
                        } else {
                            JOptionPane.showMessageDialog(
                                    VideoPlayerPanel.this,
                                    "Поддерживаются только следующие форматы: " + String.join(", ", SUPPORTED_FORMATS),
                                    "Ошибка",
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                            VideoPlayerPanel.this,
                            "Ошибка при обработке файла: " + e.getMessage(),
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }

    private boolean isSupportedFormat(File file) {
        String fileName = file.getName().toLowerCase();
        for (String format : SUPPORTED_FORMATS) {
            if (fileName.endsWith(format)) {
                return true;
            }
        }
        return false;
    }

    public void playVideo(Path videoPath) {
        String videoName = videoPath.getFileName().toString();
        currentVideoName = videoName;

        // Получаем прогресс из базы данных
        long progress = databaseManager.getProgress(videoName);

        // Запускаем воспроизведение
        mediaPlayer.mediaPlayer().media().start(videoPath.toString());

        // Устанавливаем позицию воспроизведения, если прогресс больше 0
        if (progress > 0) {
            mediaPlayer.mediaPlayer().controls().setTime(progress);
        }

        // Уведомляем о начале воспроизведения видео с сервера
        onVideoStarted(videoPath, false);
    }

    public void playLocalVideo(Path videoPath) {
        String videoName = videoPath.getFileName().toString();
        currentVideoName = videoName;

        // Получаем прогресс из базы данных
        long progress = databaseManager.getProgress(videoName);

        // Запускаем воспроизведение
        mediaPlayer.mediaPlayer().media().start(videoPath.toString());

        // Устанавливаем позицию воспроизведения, если прогресс больше 0
        if (progress > 0) {
            mediaPlayer.mediaPlayer().controls().setTime(progress);
        }

        // Уведомляем о начале воспроизведения локального видео
        onVideoStarted(videoPath, true);
    }

    /**
     * Метод, вызываемый при начале воспроизведения видео
     * @param videoPath путь к видеофайлу
     * @param isLocal true, если видео локальное, false если с сервера
     */
    protected void onVideoStarted(Path videoPath, boolean isLocal) {
        // Этот метод будет переопределен в VideoClient
    }

    public void release() {
        controlPanel.release();
        mediaPlayer.release();
    }
}