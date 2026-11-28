-- MySQL 8. 기존 v2 스키마에 1회 적용. 자동 DDL update로 이미 생성했다면 중복 실행하지 않는다.
-- 데이터 연결은 서버 시작 및 첫 설정 조회/변경 또는 학생 등록/수정에서 학원 단위 트랜잭션으로 수행한다.
-- 기존 grade/class_name 및 완료 첨삭 snapshot은 삭제하거나 다시 쓰지 않는다.
ALTER TABLE academies ADD COLUMN settings_initialized BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE academy_grades (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    grade_group VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_academy_grades_name UNIQUE (academy_id, name),
    CONSTRAINT fk_academy_grades_academy FOREIGN KEY (academy_id) REFERENCES academies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE academy_classes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    academy_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_academy_classes_name UNIQUE (academy_id, name),
    CONSTRAINT fk_academy_classes_academy FOREIGN KEY (academy_id) REFERENCES academies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE students ADD COLUMN grade_id BIGINT NULL;
ALTER TABLE students ADD COLUMN class_id BIGINT NULL;
CREATE INDEX idx_students_grade_id ON students(grade_id);
CREATE INDEX idx_students_class_id ON students(class_id);
ALTER TABLE students ADD CONSTRAINT fk_students_grade FOREIGN KEY (grade_id) REFERENCES academy_grades(id);
ALTER TABLE students ADD CONSTRAINT fk_students_class FOREIGN KEY (class_id) REFERENCES academy_classes(id);
