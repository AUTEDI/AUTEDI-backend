-- 수동 AI 인사이트 MVP. MySQL 8, 백업 후 신규 코드 배포 전에 1회 실행한다.
-- 자동 생성/작업 큐/인사이트 이력 테이블은 없다. 기존 학생·저장본은 변경하지 않는다.
CREATE TABLE student_insights (
    student_id BIGINT NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(60) NOT NULL,
    schema_version INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_results JSON NOT NULL,
    result JSON NOT NULL,
    used_result_count INT NOT NULL,
    analysis_from DATE NOT NULL,
    analysis_to DATE NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    generated_by BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(student_id),
    INDEX idx_student_insights_generated_by(generated_by),
    CONSTRAINT fk_student_insights_student FOREIGN KEY(student_id) REFERENCES students(id),
    CONSTRAINT fk_student_insights_user FOREIGN KEY(generated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
