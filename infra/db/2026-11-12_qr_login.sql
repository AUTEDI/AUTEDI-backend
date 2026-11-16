-- #41 QR 로그인: prod(ddl-auto: validate) 스키마 적용 스크립트.
-- 적용 시점: develop을 main으로 머지하기 전에 prod DB에 수동 적용한다.
-- 기준: Hibernate(MySQL 8.0)가 ddl-auto=create로 생성한 정의를 SHOW CREATE TABLE로 확인해 작성했다.
--       status는 기존 테이블(users, worksheets 등)과 같은 MySQL enum 타입이다.
-- 주의: QrSessionStatus 값을 바꾸면 enum 정의도 함께 바꿔야 한다.
-- 참고: uploaded_at(QR 하나당 업로드 한 번)은 이후에 추가한 컬럼이라 Hibernate 생성 결과가 아니라 같은 규칙으로 직접 적었다. 적용 전에 검증한다.

CREATE TABLE `qr_session` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `worksheet_id` bigint DEFAULT NULL,
  `session_id` varchar(36) NOT NULL,
  `qr_token` varchar(64) NOT NULL,
  `status` enum('APPROVED','COMPLETED','EXPIRED','PENDING','REJECTED','SCANNED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qr_session_session_id` (`session_id`),
  UNIQUE KEY `uk_qr_session_qr_token` (`qr_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `refresh_tokens`
  ADD COLUMN `qr_session_id` varchar(36) NULL;
