package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 실제 마이그레이션의 테이블 엔진/문자셋 옵션만 제거해 H2 MySQL 모드에서 기존 자료의 업그레이드를 확인한다. */
class StudentWorksheetMigrationTest {

    @Test
    void migrationPreservesOldRowsAndAddsDefaultsAndForeignKeys() throws Exception {
        try (Connection connection =
                DriverManager.getConnection("jdbc:h2:mem:b1_" + UUID.randomUUID() + ";MODE=MySQL")) {
            try (Statement sql = connection.createStatement()) {
                sql.execute("CREATE TABLE academies (id bigint PRIMARY KEY)");
                sql.execute("CREATE TABLE users (id bigint PRIMARY KEY)");
                sql.execute(
                        """
                        CREATE TABLE worksheets (
                          id bigint PRIMARY KEY, academy_id bigint NOT NULL, user_id bigint NOT NULL,
                          status enum('DRAFT','PROCESSING','READY','FAILED') NOT NULL, version int NOT NULL)
                        """);
                sql.execute("INSERT INTO academies VALUES (1)");
                sql.execute("INSERT INTO users VALUES (1)");
                sql.execute("INSERT INTO worksheets VALUES (1, 1, 1, 'READY', 9), (2, 1, 1, 'DRAFT', 1)");

                String migration = Files.readString(
                        Path.of("infra/db/2026-11-26_student_worksheet_common.sql"), StandardCharsets.UTF_8);
                migration = migration
                        .replaceAll("(?m)^\\s*--.*$", "")
                        .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
                for (String command : migration.split(";")) {
                    if (!command.isBlank()) sql.execute(command);
                }

                try (ResultSet rows = sql.executeQuery(
                        "SELECT status, version, save_status, student_id, title, completed_at FROM worksheets ORDER BY id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("READY");
                    assertThat(rows.getInt("version")).isEqualTo(9);
                    assertThat(rows.getString("save_status")).isEqualTo("UNSAVED");
                    assertThat(rows.getObject("student_id")).isNull();
                    assertThat(rows.getString("title")).isNull();
                    assertThat(rows.getObject("completed_at")).isNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("status")).isEqualTo("DRAFT");
                    assertThat(rows.getString("save_status")).isEqualTo("UNSAVED");
                    assertThat(rows.next()).isFalse();
                }

                // 구버전 INSERT는 신규 컬럼을 몰라도 기본값으로 계속 동작한다.
                sql.execute(
                        "INSERT INTO worksheets(id, academy_id, user_id, status, version) VALUES(3,1,1,'FAILED',2)");
                sql.execute(
                        """
                        INSERT INTO students(academy_id, name, grade, created_at, updated_at)
                        VALUES(1, 'Student', 'unrestricted-grade', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """);
                sql.execute("UPDATE worksheets SET student_id = 1, last_saved_by = 1 WHERE id = 1");

                SQLException missingStudent = assertThrows(
                        SQLException.class, () -> sql.execute("UPDATE worksheets SET student_id = 999 WHERE id = 2"));
                assertThat(missingStudent.getSQLState()).startsWith("23");
                SQLException missingSaver = assertThrows(
                        SQLException.class,
                        () -> sql.execute("UPDATE worksheets SET last_saved_by = 999 WHERE id = 2"));
                assertThat(missingSaver.getSQLState()).startsWith("23");
                SQLException referencedStudent =
                        assertThrows(SQLException.class, () -> sql.execute("DELETE FROM students WHERE id = 1"));
                assertThat(referencedStudent.getSQLState()).startsWith("23");
            }
        }
    }
}
