-- A3: 학생 메모 테이블 추가 (MySQL 8.0).
-- prod는 ddl-auto: validate이므로 새 애플리케이션 배포 전에 수동 적용한다.
-- 선행 조건: 2026-11-26_student_worksheet_common.sql(students 테이블)이 먼저 적용되어 있어야 한다.
-- 적용 전 SHOW CREATE TABLE students/users로 id 타입(bigint)을 확인한다.
-- DDL은 MySQL에서 자동 커밋된다. 롤백 트랜잭션을 가정하지 않는다.
-- 메모는 신규 테이블이므로 기존 데이터에 영향이 없다. 수정·삭제 정책이 정해지기 전이라 updated_at·deleted_at은 두지 않는다.

CREATE TABLE `student_memos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `content` varchar(1000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 학생별 최신순 조회용. student_id가 맨 앞이라 student_id 외래 키의 인덱스 역할도 한다.
CREATE INDEX `idx_student_memos_student_created` ON `student_memos` (`student_id`, `created_at`, `id`);

ALTER TABLE `student_memos`
  ADD CONSTRAINT `fk_student_memos_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`);
ALTER TABLE `student_memos`
  ADD CONSTRAINT `fk_student_memos_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`);

-- 적용 후 SHOW CREATE TABLE student_memos를 확인하고 새 코드로 schema validate를 수행한다.
