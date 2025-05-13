package org.example;

import java.sql.*;

public class DatabaseManager {
    private static final String DATABASE_URL = "jdbc:sqlite:video_progress.db";
    private static final String CREATE_TABLE_QUERY = """
        CREATE TABLE IF NOT EXISTS video_progress (
            video_name TEXT PRIMARY KEY,
            progress INTEGER
        )
    """;

    public DatabaseManager() {
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             Statement stmt = conn.createStatement()) {
            // Создаем таблицу, если она не существует
            stmt.execute(CREATE_TABLE_QUERY);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveProgress(String videoName, long progress) {
        String query = "INSERT OR REPLACE INTO video_progress (video_name, progress) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, videoName);
            pstmt.setLong(2, progress);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getProgress(String videoName) {
        String query = "SELECT progress FROM video_progress WHERE video_name = ?";
        try (Connection conn = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, videoName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("progress");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0; // Если прогресс не найден, возвращаем 0
    }
}