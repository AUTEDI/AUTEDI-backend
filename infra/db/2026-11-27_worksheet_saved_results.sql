-- B5: 학습지별 마지막 명시적 임시저장본 (MySQL 8.0).
-- B1 공통 모델 마이그레이션 적용 후, 새 애플리케이션 배포 전에 한 번 적용한다.
-- 기존 DB를 백업하고 worksheets/users/worksheet_generations PK가 signed bigint인지 확인한다.
-- 운영 DB에는 자동 적용하지 않는다. MySQL DDL은 자동 커밋된다.
-- 기존 학습지 상태와 저장 시각은 변경하지 않는다. 첫 명시적 저장에서만 행을 만든다.

CREATE TABLE `worksheet_saved_results` (
  `worksheet_id` bigint NOT NULL,
  `revision` bigint NOT NULL,
  `schema_version` int NOT NULL DEFAULT 1,
  `source_file_hash` varchar(64) NOT NULL,
  `source_generation_id` bigint DEFAULT NULL,
  `saved_by` bigint NOT NULL,
  `saved_at` datetime(6) NOT NULL,
  `snapshot` json NOT NULL,
  `version` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`worksheet_id`),
  KEY `idx_saved_results_saved_by` (`saved_by`),
  KEY `idx_saved_results_generation` (`source_generation_id`),
  CONSTRAINT `fk_saved_results_worksheet` FOREIGN KEY (`worksheet_id`) REFERENCES `worksheets` (`id`),
  CONSTRAINT `fk_saved_results_user` FOREIGN KEY (`saved_by`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_saved_results_generation` FOREIGN KEY (`source_generation_id`) REFERENCES `worksheet_generations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- worksheet_id PK로 최신본 한 개만 유지한다. revision은 저장 횟수이며 이력을 보관하지 않는다.
-- snapshot은 저장 당시 ReviewResponse와 삭제 행을 포함한 첨삭/코멘트의 복원용 편집 값이다.
-- FK에 ON DELETE CASCADE를 지정하지 않는다. 논리 삭제 시 저장본과 원본 파일을 보존한다.
-- 적용 후 SHOW CREATE TABLE worksheet_saved_results와 새 코드의 schema validate를 확인한다.
