package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SQL 자체의 PK/FK·기존 행 보존을 H2 MySQL 모드로 검증한다. 운영 MySQL 적용 검증은 별도다. */
class WorksheetSavedResultMigrationTest {
    @Test
    void addsOneSnapshotPerWorksheetWithoutChangingExistingWorksheets() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:b5_migration_" + UUID.randomUUID() + ";MODE=MySQL");
                var sql = db.createStatement()) {
            sql.execute("create table users(id bigint primary key)");
            sql.execute("create table worksheets(id bigint primary key, status varchar(30), version int)");
            sql.execute("create table worksheet_generations(id bigint primary key)");
            sql.execute("insert into users values(1)");
            sql.execute("insert into worksheets values(1,'READY',9),(2,'DRAFT',1)");
            sql.execute("insert into worksheet_generations values(1)");
            String migration = Files.readString(Path.of("infra/db/2026-11-27_worksheet_saved_results.sql"))
                    .replaceAll("(?m)^\\s*--.*$", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
            for (String command : migration.split(";")) if (!command.isBlank()) sql.execute(command);
            String insert =
                    "insert into worksheet_saved_results(worksheet_id,revision,source_file_hash,source_generation_id,saved_by,saved_at,snapshot,created_at,updated_at) values(%d,1,'hash',%s,%d,current_timestamp,'{}',current_timestamp,current_timestamp)";
            sql.execute(insert.formatted(1, "1", 1));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(1, "1", 1)));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(999, "1", 1)));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(2, "999", 1)));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(2, "1", 999)));
            sql.execute(insert.formatted(2, "null", 1));
            try (var row = sql.executeQuery("select status,version from worksheets where id=1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("READY");
                assertThat(row.getInt(2)).isEqualTo(9);
            }
            try (var row = sql.executeQuery(
                    "select schema_version,version from worksheet_saved_results where worksheet_id=1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isEqualTo(1);
                assertThat(row.getInt(2)).isZero();
            }
        }
    }
}
