-- 읽기 전용 사전 점검 자료. 자동 실행되지 않는다.
-- 실제 접속 대상이 develop DB인지 먼저 확인하고, 결과가 예상과 다르면 적용을 중단한다.
SELECT DATABASE() AS target_database, VERSION() AS mysql_version;

SELECT TABLE_NAME, ENGINE, ROW_FORMAT, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('worksheet_generations', 'worksheet_generation_results', 'corrections');

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'worksheet_generations' AND COLUMN_NAME = 'request_ai_profile')
    OR (TABLE_NAME = 'corrections' AND COLUMN_NAME IN ('anchor', 'v11_details'))
    OR (TABLE_NAME = 'worksheet_generation_results' AND COLUMN_NAME = 'validation_result'));

SHOW CREATE TABLE corrections;
SHOW CREATE TABLE worksheet_generation_results;

SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
WHERE tc.TABLE_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'corrections'
  AND tc.CONSTRAINT_TYPE = 'CHECK';

-- 기존 v1/미분류 규칙에 어긋나는 데이터가 있는지 복제 DB에서 먼저 확인한다.
SELECT COUNT(*) AS incompatible_existing_rows
FROM corrections
WHERE NOT (
  (action IS NULL AND issue_kinds IS NULL AND anchor IS NULL
    AND location_uncertain IS NULL AND warnings IS NULL AND requires_review IS NULL)
  OR
  (action IS NOT NULL AND action IN ('REPLACE', 'GUIDANCE') AND feedback_type IS NOT NULL
    AND feedback_type IN ('CORRECTION', 'SUGGESTION') AND issue_kinds IS NOT NULL AND anchor IS NOT NULL
    AND location_uncertain IS NOT NULL AND warnings IS NOT NULL AND requires_review IS NOT NULL
    AND (action <> 'GUIDANCE' OR area = 'CONTENT_SUMMARY'))
);

SELECT contract_version, validation_version, COUNT(*) AS result_count
FROM worksheet_generation_results
GROUP BY contract_version, validation_version;

-- 잠금/실행 중인 세션 확인. 권한과 결과 범위는 DB 계정에 따라 다르다.
SHOW FULL PROCESSLIST;
