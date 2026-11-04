# 프론트엔드 개발에 필요한 추가 API 목록

## 1. 인증 관련 API

### 1.1 간단 로그인 (자동 회원가입)
**POST /api/v1/auth/login**

Request:
```json
{
  "email": "user@example.com",
  "nickname": "홍길동",
  "role": "VISITOR", // or "STAFF"
  "managedBoothId": 1 // STAFF인 경우 관리할 부스 ID (optional)
}
```

Response (200 OK):
```json
{
  "accessToken": "eyJhbGc...",
  "userId": 123,
  "email": "user@example.com",
  "nickname": "홍길동",
  "role": "VISITOR"
}
```

> **동작 방식**: email이 없으면 자동 회원가입, 있으면 로그인 (upsert)
> email은 유저 구분용 (필수), nickname은 UI 표시용 (필수)
> 비밀번호 없음 - 간단하게!
> JWT에 userId, email, role 포함

---

## 2. 부스 관련 API

### 2.1 부스 목록 조회
**GET /api/v1/booths**

Query Parameters:
- `universityId` (optional): 대학 필터링

Response (200 OK):
```json
{
  "booths": [
    {
      "boothId": 1,
      "boothName": "치킨 부스",
      "description": "맛있는 치킨 체험",
      "universityName": "서울대학교",
      "status": "OPEN",
      "capacity": 50,
      "currentWaiting": 523,
      "estimatedWaitTime": 50
    }
  ]
}
```

### 2.2 부스 상세 조회
**GET /api/v1/booths/{boothId}**

Response (200 OK):
```json
{
  "boothId": 1,
  "boothName": "치킨 부스",
  "description": "맛있는 치킨 체험",
  "universityName": "서울대학교",
  "status": "OPEN",
  "capacity": 50,
  "currentPeople": 30,
  "totalWaiting": 523,
  "estimatedWaitTime": 50,
  "openTime": "09:00:00",
  "closeTime": "18:00:00"
}
```

---

## 3. 사용자 대기 목록 조회

### 3.1 내 대기 목록 조회
**GET /api/v1/waitings/my**

Response (200 OK):
```json
{
  "waitings": [
    {
      "boothId": 1,
      "boothName": "치킨 부스",
      "position": 10,
      "totalWaiting": 523,
      "estimatedWaitTime": 50,
      "status": "WAITING", // WAITING, CALLED
      "registeredAt": "2025-11-20T10:00:00Z"
    }
  ]
}
```

---

## 4. 스태프용 대시보드 API

### 4.1 부스 현황 조회 (스태프용)
**GET /api/v1/booths/{boothId}/status**

Response (200 OK):
```json
{
  "boothId": 1,
  "boothName": "치킨 부스",
  "currentPeople": 30,
  "capacity": 50,
  "totalWaiting": 523,
  "todayStats": {
    "totalCalled": 150,
    "totalEntered": 120,
    "totalNoShow": 30,
    "totalCompleted": 90
  }
}
```

### 4.2 호출 대기 목록 조회 (스태프용)
**GET /api/v1/booths/{boothId}/called-list**

Response (200 OK):
```json
{
  "calledList": [
    {
      "waitingId": 123,
      "userId": 456,
      "nickname": "홍길동",
      "position": 1,
      "status": "CALLED",
      "calledAt": "2025-11-20T10:45:00Z",
      "remainingTime": 240 // 노쇼까지 남은 시간(초)
    }
  ]
}
```

---

## 5. FCM 토큰 등록

### 5.1 FCM 토큰 저장
**POST /api/v1/users/fcm-token**

Request:
```json
{
  "fcmToken": "fK3j2..."
}
```

Response (200 OK):
```json
{
  "success": true
}
```

Error Responses:

**400 Bad Request - FCM 토큰이 유효하지 않은 경우**
```json
{
  "status": 400,
  "code": "INVALID_FCM_TOKEN",
  "message": "FCM 토큰은 필수입니다."
}
```

**400 Bad Request - 알림이 비활성화된 사용자**
```json
{
  "status": 400,
  "code": "NOTIFICATION_DISABLED",
  "message": "알림이 비활성화되어 있습니다."
}
```

**404 Not Found - 사용자를 찾을 수 없는 경우**
```json
{
  "status": 404,
  "code": "USER_NOT_FOUND",
  "message": "사용자를 찾을 수 없습니다."
}
```

> 사용자가 대기 등록 시 푸시 알림을 받기 위한 FCM 토큰 저장
> 프론트에서 Firebase SDK로 토큰 획득 후 전송
> **주의**: FCM 토큰은 필수이며, 알림이 활성화된 사용자만 등록 가능

---

**Last Updated**: 2025-01-08
