package org.example;

import org.example.model.AcceptedUser;
import org.example.model.ApplicationData;
import org.example.model.JobPosition;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.sql.DriverManager.getConnection;

public class DatabaseManager {

    //    private static final String DB_URL = "jdbc:postgresql://localhost:5432/usta_db";
//    private static final String DB_USER = "postgres";
//private static final String DB_PASSWORD = "1";/
    private static final String DB_URL = "jdbc:postgresql://dpg-d1ovc3mr433s73cq8v60-a.oregon-postgres.render.com/nisbot";
    private static final String DB_USER = "nisbot_user";
    private static final String DB_PASSWORD = "wbxWGtUCvqoEdaHslkuSJGwgkjmVQ59F";


    public List<JobPosition> getJobPositions() {
        List<JobPosition> positions = new ArrayList<>();

        String sql = "SELECT id, name, is_active FROM job_positions WHERE is_active = true";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                positions.add(new JobPosition(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return positions;
    }

    public JobPosition getJobPositionById(int id) {
        String sql = "SELECT id, name, is_active FROM job_positions WHERE id = ?";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new JobPosition(
                        rs.getInt("id"),
                        rs.getString("name")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void saveUserApplication(long userId, Map<String, String> data, String username) {
        String sql = """
                INSERT INTO user_applications (
                    user_id, full_name, phone, certificates, branch, cv_file_id, job_position, username
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name,
                    phone = EXCLUDED.phone,
                    certificates = EXCLUDED.certificates,
                    branch = EXCLUDED.branch,
                    cv_file_id = EXCLUDED.cv_file_id,
                    job_position = EXCLUDED.job_position,
                    username = EXCLUDED.username
                """;

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setString(2, data.getOrDefault("full_name", ""));
            pstmt.setString(3, data.getOrDefault("phone", ""));
            pstmt.setString(4, data.getOrDefault("certificates", ""));
            pstmt.setString(5, data.getOrDefault("branch", ""));
            pstmt.setString(6, data.getOrDefault("cv_file_id", ""));
            String jobPositionStr = data.getOrDefault("job_position", "0");
            System.out.println("job_position: " + jobPositionStr);

            pstmt.setLong(7, Long.parseLong(jobPositionStr));
            pstmt.setString(8, username);

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getUserApplicationsForAdmin() {
        List<String> applications = new ArrayList<>();

        String sql = "SELECT * FROM user_applications";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                long userId = rs.getLong("user_id");
                String fullName = rs.getString("full_name");
                String phone = rs.getString("phone");
                String certificates = rs.getString("certificates");
                String branch = rs.getString("branch");
                String cvFileId = rs.getString("cv_file_id");
                int jobPositionId = rs.getInt("job_position");
                String jobName = getJobPositionNameById(String.valueOf(jobPositionId));
                String username = rs.getString("username");

                StringBuilder sb = new StringBuilder();
                sb.append("<b>👤 Foydalanuvchi ma'lumotlari</b>\n");
                sb.append("🆔 User ID: <code>").append(userId).append("</code>\n");
                sb.append("👨‍💼 Ism: <b>").append(fullName).append("</b>\n");
                sb.append("📞 Telefon: ").append(phone).append("\n");
                sb.append("👤 Username: @").append(isEmpty(username) ? "Kiritilmagan" : username).append("\n");
                sb.append("🏅 Sertifikatlar: ").append(certificates).append("\n");
                sb.append("🏢 Filial: ").append(branch).append("\n");
                sb.append("💼 Ish o‘rni: ").append(jobName).append("\n");
                if (!cvFileId.isEmpty()) sb.append("📄 CV: <code>").append(cvFileId).append("</code>\n");

                applications.add(sb.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return applications;
    }

    public List<ApplicationData> getAllApplications() {
        List<ApplicationData> applications = new ArrayList<>();
        String sql = "SELECT user_id, full_name, phone, certificates, branch, cv_file_id, job_position FROM user_applications";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                ApplicationData app = new ApplicationData();
                app.setUserId(rs.getLong("user_id"));
                app.setFullName(rs.getString("full_name"));
                app.setPhone(rs.getString("phone"));
                app.setCertificates(rs.getString("certificates"));
                app.setBranch(rs.getString("branch"));
                app.setCvFileId(rs.getString("cv_file_id"));
                app.setJobPosition(String.valueOf(rs.getInt("job_position"))); // Store as String for compatibility
                applications.add(app);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return applications;
    }

    public String getJobPositionNameById(String jobId) {
        String sql = "SELECT name FROM job_positions WHERE id = ?";
        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, Integer.parseInt(jobId));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Noma'lum lavozim";
    }

    public ApplicationData getApplicationByUserId(Long userId) {
        String query = "SELECT user_id, full_name, phone, certificates, branch, cv_file_id, job_position, username FROM user_applications WHERE user_id = ?";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                ApplicationData app = new ApplicationData();
                app.setUserId(rs.getLong("user_id"));
                app.setFullName(rs.getString("full_name"));
                app.setPhone(rs.getString("phone"));
                app.setCertificates(rs.getString("certificates"));
                app.setBranch(rs.getString("branch"));
                app.setCvFileId(rs.getString("cv_file_id"));
                app.setJobPosition(String.valueOf(rs.getInt("job_position"))); // Store as String
                app.setUsername(rs.getString("username"));
                return app;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveAcceptedApplication(Long userId, String fullName, String phone, String username, String certificates, String branch, String cvFileId, String jobPosition) {
        String sql = """
                INSERT INTO accepted_applications (
                    user_id, full_name, phone_number, username, certificates, branch, cv_file_id, job_position
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setString(2, fullName);
            pstmt.setString(3, phone);
            pstmt.setString(4, username);
            pstmt.setString(5, certificates);
            pstmt.setString(6, branch);
            pstmt.setString(7, cvFileId);
            pstmt.setInt(8, Integer.parseInt(jobPosition)); // Convert to INTEGER

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteUserApplication(Long userId) {
        String sql = "DELETE FROM user_applications WHERE user_id = ?";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<AcceptedUser> getAllAcceptedUsers() {
        List<AcceptedUser> list = new ArrayList<>();
        String sql = "SELECT * FROM accepted_applications";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                AcceptedUser user = new AcceptedUser();
                user.setUserId(rs.getLong("user_id"));
                user.setFullName(rs.getString("full_name"));
                user.setPhoneNumber(rs.getString("phone_number"));
                user.setUsername(rs.getString("username"));
                user.setCertificates(rs.getString("certificates"));
                user.setBranch(rs.getString("branch"));
                user.setCvFileId(rs.getString("cv_file_id"));
                user.setJobPosition(String.valueOf(rs.getInt("job_position"))); // Store as String
                list.add(user);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public AcceptedUser getAcceptedUserById(Long userId) {
        String sql = "SELECT * FROM accepted_applications WHERE user_id = ?";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                AcceptedUser user = new AcceptedUser();
                user.setUserId(rs.getLong("user_id"));
                user.setFullName(rs.getString("full_name"));
                user.setPhoneNumber(rs.getString("phone_number"));
                user.setUsername(rs.getString("username"));
                user.setCertificates(rs.getString("certificates"));
                user.setBranch(rs.getString("branch"));
                user.setCvFileId(rs.getString("cv_file_id"));
                user.setJobPosition(String.valueOf(rs.getInt("job_position"))); // Store as String
                return user;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Map<String, Object>> getAllJobPositionsWithStatus() {
        List<Map<String, Object>> jobList = new ArrayList<>();

        String sql = "SELECT id, name, is_active FROM job_positions";

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> jobMap = new HashMap<>();
                jobMap.put("id", rs.getInt("id"));
                jobMap.put("name", rs.getString("name"));
                jobMap.put("is_active", rs.getBoolean("is_active"));
                jobList.add(jobMap);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return jobList;
    }

    public boolean getJobPositionStatusById(int jobId) {
        String sql = "SELECT is_active FROM job_positions WHERE id = ?";
        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, jobId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_active");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void updateJobPositionStatus(int jobId, boolean isActive) {
        String sql = "UPDATE job_positions SET is_active = ? WHERE id = ?";
        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, isActive);
            stmt.setInt(2, jobId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveJobPosition(String name, String description, String requirements, boolean isActive) {
        String sql = """
                INSERT INTO job_positions (name, is_active)
                VALUES (?, ?)
                """;

        try (Connection conn = getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setBoolean(2, isActive);
            // Ignore description and requirements since they are not in the schema

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}