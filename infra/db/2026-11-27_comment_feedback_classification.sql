-- B8: 기존 교사 코멘트는 GENERAL로 유지한다. 운영 적용 전 백업 후 1회 실행한다.
-- prod의 ddl-auto=validate 배포 전에 적용한다. corrections.id의 타입이 bigint인지 확인한다.
-- MySQL DDL은 자동 커밋된다. 부분 적용 시 현재 스키마 확인 후 남은 구문만 실행한다.
-- 기존 최종/임시 저장 JSON과 schema_version=1은 변경하지 않는다. 새 저장은 애플리케이션에서 v2를 기록한다.
ALTER TABLE comments ADD COLUMN feedback_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE comments ADD COLUMN area VARCHAR(50) NULL;
ALTER TABLE comments ADD COLUMN issue_kinds JSON NULL;
ALTER TABLE comments ADD COLUMN correction_id BIGINT NULL;
CREATE INDEX idx_comments_correction ON comments (correction_id);
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_correction FOREIGN KEY (correction_id) REFERENCES corrections(id);
