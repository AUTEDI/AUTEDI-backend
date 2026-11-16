# v1.1 비교 실험 입력

[cases.json](cases.json)은 세 가지 조건의 실행 점검용 입력이다.

| ID | 내용 | 선택 영역 |
| --- | --- | --- |
| grammar-error | She go to school every day. | GRAMMAR_SPELLING |
| improvable-structure | 올바르지만 I로 시작하는 세 문장이 반복되는 글 | SENTENCE_STRUCTURE |
| clean-text | 자연스럽고 올바른 한 문장 | GRAMMAR_SPELLING, SENTENCE_STRUCTURE |

`typed-demo`의 PNG는 대응하는 txt와 일치하도록 소프트웨어로 그린 **합성 인쇄체**다. 실제 학생 손글씨의 인식·좌표·교육적 품질을 입증하는 자료가 아니다. AI 정답 응답을 넣은 파일도 아니다. 실제 모델 비교에는 확인된 손글씨 이미지·확정 전사와 평가 기준을 별도 manifest로 준비한다.

입력 경로는 manifest 위치 기준이다. 확정 전사의 앞뒤 공백·빈 줄·마지막 개행을 유지하며 두 레벨에서 같은 내용을 사용한다. `--check`는 키·실제 API 없이 입력·계획만 생성한다.

실행 코드와 보고서 설명: [v1.1 별도 비교 실험](../../../../../../docs/ai_test_docs/AI-v1.1-별도-비교실험.md).

별도 손글씨 입력: [test11-cases.json](test11-cases.json)은 상위 `test_data/test11.jpg`와 직접 전사한 [본문 텍스트](../../../ocr/test11-transcription.txt)를 연결한다. 기본 합성 입력 목록은 그대로 유지한다. [test11 실제 요청 실행 방법](../../../../../../docs/ai_test_docs/AI-v1.1-test11-실제요청-실행방법.md)을 참고한다.
