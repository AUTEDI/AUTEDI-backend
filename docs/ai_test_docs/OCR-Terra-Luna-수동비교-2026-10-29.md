# Terra·Luna 수동 판독 비교 — 2026-10-29

## 평가 범위와 한계

test3.jpg, test8.jpg, test7.jpg를 직접 보고 작성한 임시 기준이다. 독립적인 인간 검수 정답이 아니며 같은 모델 출력들을 먼저 본 상태에서 판독하여 편향 가능성이 있다. 이 점수를 모델의 일반 정확도로 해석하지 않는다.

평가 대상은 영어 본문이다. 인쇄된 양식, 이름·날짜·제목·낙서, test7 하단 다른 종이에 걸쳐 잘린 글은 정량 평가에서 제외했다. 제목 누락은 별도 지적한다.
소문자로 통일하고 따옴표 및 문장부호를 공백으로 치환한다. 아포스트로피는 보존한다.
줄바꿈으로 갈라진 pla/-stic과 bookca/-se는 각각 plastic, bookcase로 연결한다. 단, Luna의 별도 So는 삽입으로 계산한다.
MASK는 판독이 애매한 단어/구간이며 분모에서 제외한다. 정렬에서 어떤 한 단어와도 일치하거나 비용 없이 삭제할 수 있다.
test8의 MASK 3개는 bag 앞의 불명확한 철자이다. bag은 읽히므로 평가한다.
test7의 MASK 3개는 It 다음 단어, the base와 found 사이 부정 표현, and he와 the answer 사이 동사이다. 비교에서도 gaw/all, wasnt/was not, got/saw를 각각 MASK로 치환해 제외한다.

점수 = 100 × (1 − (단어 대체 + 누락 + 삽입)/평가 단어 수). 이는 제한된 구간의 1−WER 점수이며 전체 원문 일치율이 아니다.

## 결과

| 사진 | 평가 단어 수 | Terra 오류 | Luna 오류 | Terra 점수 | Luna 점수 |
|---|---:|---:|---:|---:|---:|
| test3 | 11 | 0 | 0 | 100% | 100% |
| test8 | 80 | 0 | 4 | 100% | 95.0% |
| test7 | 148 | 0 | 4 | 100% | 97.3% |
| 합계 | 239 | 0 | 8 | 100% | 96.7% |

Luna test8: bag→bug 두 곳, Sudenly→Suddenly, 마지막 bag→ladybug(인접 애매한 단어와 병합된 표현).
Luna test7: sludge→[unclear], agein→again, So 추가, droped→dropped.
test3 원문은 소문자 mom 및 마지막 마침표로 판독했다. Terra의 마지막 쉼표와 Luna의 Mom은 위 정규화 점수에는 반영되지 않는다.
test7의 손글씨 제목은 Luna 전사에서 누락되었으나 본문 점수에는 포함되지 않는다.
test7 하단 글은 다른 종이에 걸쳐 잘렸으므로, 해당 부분을 Luna의 확정적인 본문 누락으로 평가하지 않는다.

## 임시 정답 본문

### test3

```text
mom came back I pretended as if nothing had ever happened
```

### test8

```text
When Nate the great sleep who calling to him
It was Rosamond she said I lost my cat pillow case
Can you help me he said Yes he asked How pillow
case look like she said It look like white
He went Annie house but she said I don't have
Rosamond pillowcase but I have Rosamond's
MASK bag I taken mistake Nate the great
think and think Sudenly he knew who were
Pillow case IT MASK bag because
Rosamond doesn't have MASK bag
```

### test7

```text
It MASK started when Nate went practice
base ball Rosamond said we can't
practice today because we have a
stolen second base The base was plastic
Octopus Nate started to look
for the base First he went to Oliver's
room Because the octopus was Oliver's
thing But the base MASK found
there Nate went to the park and he
found one octopus arm and sludge
found another arm But there were
no more arms Nate the great ate
pan cake and he went to Annie's
house Annie had a third base it
was a dog bone that Fang didn't
eat And Annie found another arm in
her house Nate thought and he MASK
the answer He went Oliver's house
agein He put the base on his bookcase
when the base practice was over
The base was droped in the side of
the wall but not in of it The base was
```

## 좌표

정답 박스를 수작업으로 어노테이션하지 않았으므로 좌표 정답률/IoU를 계산하지 않았다.
test8 첫 본문 줄 great의 Luna x=0.305..0.390은 실제 단어보다 왼쪽으로 치우쳐 보인다. Terra x=0.353..0.437이 더 가깝다.
test7에서 Luna는 여러 본문 박스를 실제 글자보다 위로 배치한 것으로 보인다. 예: It y=0.149..0.168, dog y=0.571..0.591.
Luna test7의 162개 박스 중 114개가 정규화 높이 0.020으로 일률적이다. 이는 정밀한 개별 글자 경계가 아니라 줄 단위 근사 박스일 가능성을 확인할 근거다.
Terra test7 좌표는 첨부 로그에서 중간이 생략되어 전체 비교가 불가능하다.
EXIF 회전과 좌표 기준의 일치 여부는 별도로 해결해야 한다.

