# Festin API 레퍼런스 (에이전트용)

에이전트가 호출할 수 있는 API 목록.

---

## 인증

### 로그인 (토큰 발급)
```
POST /api/v1/auth/login
```

**VISITOR 토큰**
```json
{
  "email": "agent_01@sim.com",
  "nickname": "SimAgent01",
  "role": "VISITOR",
  "managedBoothId": null
}
```

**STAFF 토큰**
```json
{
  "email": "staff_booth2@sim.com",
  "nickname": "StaffBooth2",
  "role": "STAFF",
  "managedBoothId": 2
}
```

**응답**
```json
{ "accessToken": "eyJ..." }
```

---

## 부스

### 부스 목록 조회
```
GET /api/v1/booths
Authorization: Bearer {token}
```

**응답** `200 OK`
```json
[
  { "id": 2, "name": "포토부스", "currentWaiting": 12 },
  { "id": 3, "name": "게임부스", "currentWaiting": 5 }
]
```

---

## 대기열 (VISITOR)

### 대기 등록
```
POST /api/v1/waitings
Authorization: Bearer {token}
```

```json
{ "boothId": 2 }
```

| 응답 코드 | 의미 |
|---------|------|
| 201 | 등록 성공, `{ "position": 7 }` |
| 400 | 최대 대기 수 초과 |
| 409 | 이미 해당 부스에 등록됨 |

### 내 순번 조회
```
GET /api/v1/waitings/booth/{boothId}
Authorization: Bearer {token}
```

| 응답 코드 | 의미 |
|---------|------|
| 200 | `{ "position": 5 }` |
| 404 | 해당 부스에 등록 안 됨 |

### 대기 취소
```
DELETE /api/v1/waitings/{boothId}
Authorization: Bearer {token}
```

| 응답 코드 | 의미 |
|---------|------|
| 200 | 취소 성공 |
| 404 | 등록된 대기 없음 |

---

## 대기열 (STAFF)

### 다음 손님 호출
```
POST /api/v1/waitings/call
Authorization: Bearer {staffToken}
```

```json
{ "boothId": 2 }
```

| 응답 코드 | 의미 |
|---------|------|
| 200 | 호출 성공. `{ "waitingId": 42, "userId": 7, "position": 1, "calledAt": "..." }` |
| 404 | 대기열 비어있음 |

### 입장 확인
```
POST /api/v1/booths/{boothId}/entrance/{waitingId}
Authorization: Bearer {staffToken}
```

| 응답 코드 | 의미 |
|---------|------|
| 200 | 입장 확인 성공 |

### 체험 완료
```
POST /api/v1/booths/{boothId}/complete/{waitingId}
Authorization: Bearer {staffToken}
```

| 응답 코드 | 의미 |
|---------|------|
| 200 | 체험 완료 처리 |

---

## 스태프 처리 사이클

```
call_next (→ waitingId 획득)
    ↓ 유저 도착 대기 (20초)
confirm_entrance
    ↓ 체험 시간 대기 (부스별 상이)
complete_experience
    ↓ 다음 손님 호출
반복
```

| 부스 | booth_id | 체험 시간 |
|------|---------|---------|
| 타코야끼 | 4 | 30초 |
| 즉석 포토부스 | 5 | 60초 |
| 캔들 공방 | 6 | 120초 |
| 링 던지기 | 7 | 20초 |
| 수제 레모네이드 | 8 | 30초 |

---

## 에이전트 행동 → API 매핑

| 에이전트 행동 | API 호출 |
|-------------|---------|
| `browse` | GET /api/v1/booths |
| `enqueue` | POST /api/v1/waitings |
| `check_position` | GET /api/v1/waitings/booth/{id} |
| `cancel` | DELETE /api/v1/waitings/{id} |
| `call_next` | POST /api/v1/waitings/call (STAFF) |
| `confirm_entrance` | POST /api/v1/booths/{id}/entrance/{waitingId} (STAFF) |
| `complete_experience` | POST /api/v1/booths/{id}/complete/{waitingId} (STAFF) |
| `exit` | 없음 (에이전트 종료) |