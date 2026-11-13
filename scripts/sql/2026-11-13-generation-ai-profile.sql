-- 10단계: 새 작업의 불변 AI 실행 프로필. 기존 작업은 NULL 그대로 두고 v1로 해석한다.
-- 운영 DB에 자동 실행하지 않는다. 기존 컬럼이 없는지 확인한 뒤 배포 전에 한 번 적용한다.
ALTER TABLE worksheet_generations ADD COLUMN request_ai_profile JSON NULL;
