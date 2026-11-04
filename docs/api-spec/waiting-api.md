# Waiting API - 사용자 대기 관리

## 📋 개요

사용자가 부스 대기열에 등록하고, 순번을 조회하며, 대기를 취소하는 API입니다.

### Base URL
```
Production: https://api.festin.shop
Development: http://localhost:8080
```

### 인증
```
Authorization: Bearer <JWT_TOKEN>
```

### 공통 에러 응답
```json
{
  "status": 400,
  "code": "ERROR_CODE",
  "message": "에러 메시지",
  "details": {}
}
```

---

## 🎫 API 명세

### 1. 대기 등록

**POST /api/v1/waitings**

**목적**: 부스 대기열에 등록

#### Request

**Headers**
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**Body**
```json
{
  "boothId": 1
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| `boothId` | Long | ✅ | > 0 |

#### Success Response

**200 Created - 신규 등록, 이미 등록됨**
```json
{
  "boothId": 1,
  "boothName": "치킨 부스",
  "position": 10,
  "totalWaiting": 523,
  "estimatedWaitTime": 50,
  "registeredAt": "2025-11-20T10:00:00Z"
}
```


| 필드 | 타입 | 설명 |
|------|------|------|
| `boothId` | Long | 부스 ID |
| `boothName` | String | 부스 이름 |
| `position` | Integer | 현재 순번 |
| `totalWaiting` | Integer | 전체 대기자 수 |
| `estimatedWaitTime` | Integer | 예상 대기 시간 (분) |
| `registeredAt` | String | 등록 시간 (ISO 8601) |

**멱등성 보장:**
- 같은 사용자가 같은 부스에 중복 요청 시 → 200 OK 반환
- 기존 대기 정보를 그대로 반환 (에러가 아님)
- `position`, `totalWaiting`은 현재 시점 기준 최신 값

#### Error Responses

**409 Conflict - 최대 대기 초과**
```json
{
  "status": 409,
  "code": "MAX_WAITING_EXCEEDED",
  "message": "최대 2개 부스까지만 대기 가능합니다.",
  "details": {
    "currentBooths": [
      { "boothId": 1, "boothName": "치킨 부스", "position": 5 },
      { "boothId": 2, "boothName": "떡볶이 부스", "position": 3 }
    ]
  }
}
```

**409 Conflict - 부스 마감**
```json
{
  "status": 409,
  "code": "BOOTH_CLOSED",
  "message": "부스가 운영 중이 아닙니다."
}
```

---

### 2. 순번 조회

**GET /api/v1/waitings/booth/{boothId}**

**목적**: 특정 부스에서 내 순번 조회

#### Request

**Headers**
```
Authorization: Bearer <JWT_TOKEN>
```

**Path Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `boothId` | Long | ✅ | 부스 ID |

#### Success Response (200 OK)
```json
{
  "boothId": 1,
  "boothName": "치킨 부스",
  "position": 8,
  "totalWaiting": 520,
  "estimatedWaitTime": 40
}
```

#### Error Responses

**404 Not Found**
```json
{
  "status": 404,
  "code": "WAITING_NOT_FOUND",
  "message": "해당 부스에 대기 중이 아닙니다."
}
```

---

### 3. 대기 취소

**DELETE /api/v1/waitings/{boothId}**

**목적**: 대기 취소

#### Request

**Headers**
```
Authorization: Bearer <JWT_TOKEN>
```

**Path Parameters**
| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `boothId` | Long | ✅ | 부스 ID |

#### Success Response (204 No Content)

**응답 본문 없음**

---

## 📝 비즈니스 규칙

### 대기 등록 제약
- 1명당 최대 2개 부스까지만 동시 대기 가능
- 같은 부스 중복 등록 불가 (멱등성 보장)
- 부스 운영 중(`OPEN`)일 때만 등록 가능

### 순번 산정
- Redis Sorted Set 기반 ZRANK 사용
- 등록 시간(timestamp) 순서대로 순번 부여
- 실시간 순번 반영 (앞사람 취소 시 자동 갱신)

---

**Last Updated**: 2025-12-17
**Version**: 1.0