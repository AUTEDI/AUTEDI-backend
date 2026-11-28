package com.ceos24.autedi.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcademySettingsMigrationTest {
    @Test
    void addsSelectionReferencesAndConstraintsWithoutLosingLegacyStudentData() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:settings_" + UUID.randomUUID() + ";MODE=MySQL");
                var sql = db.createStatement()) {
            sql.execute("create table academies(id bigint primary key)");
            sql.execute("create table students(id bigint primary key, grade varchar(50), class_name varchar(100))");
            sql.execute("insert into academies values(1),(2)");
            sql.execute("insert into students values(1,'old grade','old class')");
            var ddl = Files.readString(Path.of("infra/db/2026-11-28_academy_settings.sql"))
                    .replaceAll("(?m)^\\s*--.*$", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
            for (String command : ddl.split(";")) if (!command.isBlank()) sql.execute(command);
            try (var rows = sql.executeQuery("select grade,class_name,grade_id,class_id from students where id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("old grade");
                assertThat(rows.getString(2)).isEqualTo("old class");
                assertThat(rows.getObject(3)).isNull();
                assertThat(rows.getObject(4)).isNull();
            }
            try (var rows = sql.executeQuery("select settings_initialized from academies where id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isFalse();
            }
            String grade =
                    "insert into academy_grades(academy_id,name,grade_group,enabled,created_at,updated_at) values(%d,'grade','CUSTOM',true,current_timestamp,current_timestamp)";
            String schoolClass =
                    "insert into academy_classes(academy_id,name,created_at,updated_at) values(%d,'class',current_timestamp,current_timestamp)";
            sql.execute(grade.formatted(1));
            sql.execute(schoolClass.formatted(1));
            assertThrows(SQLException.class, () -> sql.execute(grade.formatted(1)));
            assertThrows(SQLException.class, () -> sql.execute(schoolClass.formatted(1)));
            sql.execute(grade.formatted(2));
            sql.execute(schoolClass.formatted(2));
            assertThrows(SQLException.class, () -> sql.execute(grade.formatted(999)));
            assertThrows(SQLException.class, () -> sql.execute("update students set class_id=999 where id=1"));
            sql.execute("update students set grade_id=1,class_id=1 where id=1");
            assertThrows(SQLException.class, () -> sql.execute("delete from academy_classes where id=1"));
            assertThrows(SQLException.class, () -> sql.execute("delete from academy_grades where id=1"));
        }
    }
}
