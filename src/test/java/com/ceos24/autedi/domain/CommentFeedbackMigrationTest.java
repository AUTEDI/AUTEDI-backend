package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommentFeedbackMigrationTest {
    @Test
    void defaultsExistingAndOldClientCommentsToGeneralAndAddsCorrectionForeignKey() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:b8_migration_" + UUID.randomUUID() + ";MODE=MySQL");
                var sql = db.createStatement()) {
            sql.execute("create table corrections(id bigint primary key)");
            sql.execute("create table comments(id bigint primary key, content text, version int)");
            sql.execute("insert into corrections values(1)");
            sql.execute("insert into comments values(1,'existing',7)");
            var migration = Files.readString(Path.of("infra/db/2026-11-27_comment_feedback_classification.sql"))
                    .replaceAll("(?m)^\\s*--.*$", "");
            for (String command : migration.split(";")) if (!command.isBlank()) sql.execute(command);
            try (var row = sql.executeQuery(
                    "select content,version,feedback_type,area,issue_kinds,correction_id from comments where id=1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("existing");
                assertThat(row.getInt(2)).isEqualTo(7);
                assertThat(row.getString(3)).isEqualTo("GENERAL");
                assertThat(row.getObject(4)).isNull();
                assertThat(row.getObject(5)).isNull();
                assertThat(row.getObject(6)).isNull();
            }
            sql.execute("insert into comments(id,content,version) values(2,'old client',1)");
            sql.execute("update comments set feedback_type='SUPPLEMENT',correction_id=1 where id=1");
            assertThrows(SQLException.class, () -> sql.execute("update comments set correction_id=999 where id=1"));
            assertThrows(SQLException.class, () -> sql.execute("delete from corrections where id=1"));
        }
    }
}
