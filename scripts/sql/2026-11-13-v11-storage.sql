-- 11단계 MySQL 8.0.16+ / InnoDB용 검토·수동 적용 자료. 자동 실행되지 않는다.
-- 백업/복구 확인, 복제 DB 검증, preflight SQL과 SHOW CREATE TABLE 검토를 먼저 수행한다.
-- 전제: request_ai_profile은 10단계에서 준비됨.
-- 전제: v11_details가 없고, CHK_corrections_contract_v1만 존재함.
-- 이미 컬럼/새 CHECK가 있거나 실제 기존 CHECK 이름·정의가 다르면 이 파일을 그대로 실행하지 않는다.
-- ddl-auto=update로 컬럼만 추가돼 있어도 CHECK 교체까지 완료됐다고 판단하지 않는다.
-- DDL은 일반 트랜잭션의 ROLLBACK으로 되돌릴 수 없다. 중간 실패 후 처음부터 재실행하지 않는다.

ALTER TABLE corrections ADD COLUMN v11_details JSON NULL;

-- 기존 CHECK 제거와 새 CHECK 추가를 한 ALTER에 함께 둔다.
-- JSON 컬럼 추가와 별도 작업이며 기존 데이터 검사/메타데이터 잠금이 발생할 수 있다.
ALTER TABLE corrections
  DROP CHECK CHK_corrections_contract_v1,
  ADD CONSTRAINT CHK_corrections_contract_v1_v11 CHECK (
    (action IS NULL AND issue_kinds IS NULL AND anchor IS NULL
      AND location_uncertain IS NULL AND warnings IS NULL AND requires_review IS NULL AND v11_details IS NULL)
    OR
    (action IS NOT NULL AND action IN ('REPLACE', 'GUIDANCE') AND feedback_type IS NOT NULL
      AND feedback_type IN ('CORRECTION', 'SUGGESTION') AND issue_kinds IS NOT NULL
      AND (anchor IS NOT NULL OR v11_details IS NOT NULL)
      AND location_uncertain IS NOT NULL AND warnings IS NOT NULL AND requires_review IS NOT NULL
      AND (action <> 'GUIDANCE' OR area = 'CONTENT_SUMMARY'))
  );

-- 적용 뒤 실제 CHECK 정의와 컬럼을 다시 확인한다.
SHOW CREATE TABLE corrections;
