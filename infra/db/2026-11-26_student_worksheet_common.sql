-- B1: 학생 및 학습지 업무 저장 공통 모델 (MySQL 8.0).
-- prod는 ddl-auto: validate이므로 새 애플리케이션 배포 전에 수동 적용한다.
-- 기존 DB를 백업하고 SHOW CREATE TABLE academies/users/worksheets로 타입을 확인한다.
-- 이 스크립트는 한 번 적용하는 마이그레이션이다. 부분 적용 후에는 현재 스키마를 확인하고 이어서 적용한다.
-- DDL은 MySQL에서 자동 커밋된다. 롤백 트랜잭션을 가정하지 않는다.
-- 이름/학년은 필수, 연락처/반은 선택. grade 허용값·전화 형식은 후속 API 정책에서 정한다.

CREATE TABLE `students` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `academy_id` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  `grade` varchar(50) NOT NULL,
  `class_name` varchar(100) DEFAULT NULL,
  `student_phone` varchar(20) DEFAULT NULL,
  `guardian_phone` varchar(20) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX `idx_students_academy_name_id` ON `students` (`academy_id`, `name`, `id`);
ALTER TABLE `students`
  ADD CONSTRAINT `fk_students_academy` FOREIGN KEY (`academy_id`) REFERENCES `academies` (`id`);

-- title/student는 업로드 직후 비어 있다. 기존 자료를 임의의 학생이나 완료 상태로 연결하지 않는다.
-- UNSAVED 기본값은 기존 행을 보존하고, 롤링 배포 중 구버전 INSERT도 허용한다.
-- 기존 worksheets.status(AI 상태)와 worksheets.version은 변경하지 않는다.
ALTER TABLE `worksheets` ADD COLUMN `title` varchar(255) DEFAULT NULL;
ALTER TABLE `worksheets` ADD COLUMN `student_id` bigint DEFAULT NULL;
ALTER TABLE `worksheets`
  ADD COLUMN `save_status` enum('COMPLETED','TEMP_SAVED','UNSAVED') NOT NULL DEFAULT 'UNSAVED';
ALTER TABLE `worksheets` ADD COLUMN `first_saved_at` datetime(6) DEFAULT NULL;
ALTER TABLE `worksheets` ADD COLUMN `last_saved_at` datetime(6) DEFAULT NULL;
ALTER TABLE `worksheets` ADD COLUMN `completed_at` datetime(6) DEFAULT NULL;
ALTER TABLE `worksheets` ADD COLUMN `last_saved_by` bigint DEFAULT NULL;
ALTER TABLE `worksheets` ADD COLUMN `deleted_at` datetime(6) DEFAULT NULL;

CREATE INDEX `idx_worksheets_student_id` ON `worksheets` (`student_id`);
CREATE INDEX `idx_worksheets_last_saved_by` ON `worksheets` (`last_saved_by`);
ALTER TABLE `worksheets`
  ADD CONSTRAINT `fk_worksheets_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`);
ALTER TABLE `worksheets`
  ADD CONSTRAINT `fk_worksheets_last_saved_by` FOREIGN KEY (`last_saved_by`) REFERENCES `users` (`id`);

-- 단순 FK는 존재 여부만 보장한다. 기관 일치는 모델과 후속 서비스에서 검증한다.
-- 저장본 snapshot 구조·월별 집계 기준·저장/삭제 상태 전이는 이 마이그레이션에서 확정하지 않는다.
-- 적용 후 SHOW CREATE TABLE students/worksheets를 확인하고 새 코드로 schema validate를 수행한다.
