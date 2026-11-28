package com.ceos24.autedi.domain.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentInsightMigrationTest {
    @Test
    void keepsExistingDataAndAllowsOnlyOneInsightPerStudentWithValidReferences() throws Exception {
        try (var db = DriverManager.getConnection(
                        "jdbc:h2:mem:insight_migration_" + UUID.randomUUID() + ";MODE=MySQL");
                var sql = db.createStatement()) {
            sql.execute("create table students(id bigint primary key,name varchar(50))");
            sql.execute("create table users(id bigint primary key)");
            sql.execute("insert into students values(1,'existing'),(2,'second')");
            sql.execute("insert into users values(1)");
            var ddl = Files.readString(Path.of("infra/db/2026-11-28_student_insights.sql"))
                    .replaceAll("(?m)^\\s*--.*$", "")
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", "");
            for (String command : ddl.split(";")) if (!command.isBlank()) sql.execute(command);
            String insert =
                    "insert into student_insights(student_id,input_hash,model,prompt_version,schema_version,status,source_results,result,used_result_count,analysis_from,analysis_to,generated_at,generated_by,created_at,updated_at) values(%d,'hash','model','v1',1,'READY','[]','{}',3,current_date,current_date,current_timestamp,%d,current_timestamp,current_timestamp)";
            sql.execute(insert.formatted(1, 1));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(1, 1)));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(999, 1)));
            assertThrows(SQLException.class, () -> sql.execute(insert.formatted(2, 999)));
            try (var row = sql.executeQuery("select name from students where id=1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("existing");
            }
        }
    }
}
