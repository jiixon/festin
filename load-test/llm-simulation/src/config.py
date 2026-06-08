import os

# Festin 서버
BASE_URL = os.getenv("FESTIN_BASE_URL", "http://localhost:8080")

# LLM API Keys
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")

# 시뮬레이션 설정
SIMULATION_PHASE = int(os.getenv("SIMULATION_PHASE", "1"))  # 1, 2, 3, 4

# 부스 ID (Festin 서버에 실제 존재하는 부스)
BOOTH_IDS = {
    "food":  int(os.getenv("BOOTH_ID_FOOD",  "4")),  # 타코야끼
    "photo": int(os.getenv("BOOTH_ID_PHOTO", "5")),  # 즉석 포토부스
    "craft": int(os.getenv("BOOTH_ID_CRAFT", "6")),  # 캔들 공방
    "game":  int(os.getenv("BOOTH_ID_GAME",  "7")),  # 링 던지기
    "drink": int(os.getenv("BOOTH_ID_DRINK", "8")),  # 수제 레모네이드
}

BOOTH_DESCRIPTIONS = {
    "food":  "간식/푸드트럭. 회전 빠르고 대기 짧음. 점심 피크.",
    "photo": "포토부스. 대기 길어도 기다리는 유저 많음. 오후 피크.",
    "craft": "만들기/체험 부스. 처리 시간 길고 가족 단위 방문. 오후 피크.",
    "game":  "게임/경품 부스. 이탈률 높음. 전 시간대 균등.",
    "drink": "술/야식 부스. 저녁 스파이크. 연타 많음.",
}

# 부스별 체험 시간 (초)
# call_next → entrance 사이: 유저 도착 대기 (30초 고정)
# entrance → complete 사이: 실제 부스 체험 시간
BOOTH_EXPERIENCE_SEC = {
    4: 30,   # 타코야끼 — 빠른 회전 (30초)
    5: 60,   # 즉석 포토부스 — 촬영+인화 (60초)
    6: 120,  # 캔들 공방 — 만들기 체험 (2분)
    7: 20,   # 링 던지기 — 게임 빠름 (20초)
    8: 30,   # 수제 레모네이드 — 음료 제조 (30초)
}
BOOTH_ARRIVAL_SEC = 20  # 호출 후 유저 도착까지 대기 시간 (초)

# 패턴별 에이전트 비율 (Phase 3 / DAU 1000 기준)
# staff는 부스당 1명 고정이므로 여기서 제외
# waiter = 축제에 왔으면 최소 절반 이상은 뭔가 기다릴 의향이 있음
PATTERN_DISTRIBUTION = {
    "waiter":        0.40,  # 대기형 (느긋 + FOMO 통합) — 축제 핵심 이용자
    "explorer":      0.20,  # 탐색형 — 여러 부스 비교 후 결정
    "leaver":        0.10,  # 이탈형 — 줄 보고 포기
    "retrier":       0.10,  # 재시도형 — 취소 후 다른 부스 이동
    "ghost_browse":  0.07,  # 유령 탐색형 — 구경만 하고 사라짐
    "ghost":         0.05,  # 유령 등록형 — 등록 후 취소 없이 사라짐
    "double_tap":    0.03,  # 연타형 — 버튼 여러 번 누름 (버그 탐지)
    "net_retry":     0.03,  # 네트워크 재시도형 (버그 탐지)
    "concurrent":    0.02,  # 동시 탭형 (버그 탐지)
}


# ============================================================
# Phase 4 — 시간대별 트래픽 구성
# ------------------------------------------------------------
# DAU 1000을 13시간(09~22시)에 흘리는 게 아니라, 피크 시간대(점심/오후/저녁)
# 별 트래픽 패턴을 파동(wave)으로 압축 재현. 각 파동마다 시간대를 골라
# 패턴 비율 + 부스 가중치를 다르게 적용 → 같은 100명이라도 점심엔 food 쏠림,
# 저녁엔 drink 연타 우세 같은 자연스러운 차이가 나온다.
# ============================================================

TIME_BAND_DESCRIPTIONS = {
    "lunch":     "점심 피크 (12:00~13:00) — 간식/푸드트럭 폭주, 빠른 회전",
    "afternoon": "오후 피크 (14:00~17:00) — 포토/만들기 체험 최고점, 대기 길어도 기다림",
    "evening":   "저녁 피크 (19:00~21:00) — 술/야식 폭주, 연타·이벤트 부스",
}

# 시간대별 패턴 비율 — 합 ≈ 1.0
TIME_BAND_PATTERNS = {
    "lunch": {
        "waiter":       0.35,
        "explorer":     0.15,
        "leaver":       0.15,  # 점심엔 줄 길면 빠르게 이동 (회전 빠른 음식)
        "retrier":      0.10,
        "ghost_browse": 0.07,
        "ghost":        0.05,
        "double_tap":   0.05,
        "net_retry":    0.05,
        "concurrent":   0.03,
    },
    "afternoon": {
        "waiter":       0.50,  # 체험형은 대기 길어도 인내
        "explorer":     0.18,
        "leaver":       0.05,
        "retrier":      0.08,
        "ghost_browse": 0.07,
        "ghost":        0.05,
        "double_tap":   0.02,
        "net_retry":    0.03,
        "concurrent":   0.02,
    },
    "evening": {
        "waiter":       0.30,
        "explorer":     0.15,
        "leaver":       0.10,
        "retrier":      0.10,
        "ghost_browse": 0.05,
        "ghost":        0.05,
        "double_tap":   0.10,  # 저녁 술/이벤트 연타 폭주
        "net_retry":    0.10,
        "concurrent":   0.05,
    },
}

# 시간대별 부스 인기도 (가중치) — concurrent 부스 지정, 인기 부스 강조 등에 사용.
# 합 1 강제 X (sampling 시 정규화).
TIME_BAND_BOOTH_WEIGHTS = {
    "lunch":     {4: 0.55, 5: 0.10, 6: 0.05, 7: 0.10, 8: 0.20},  # food 폭주
    "afternoon": {4: 0.10, 5: 0.35, 6: 0.35, 7: 0.10, 8: 0.10},  # photo/craft
    "evening":   {4: 0.10, 5: 0.10, 6: 0.05, 7: 0.10, 8: 0.65},  # drink 폭주
}

BANDS_CYCLE = ["lunch", "afternoon", "evening"]


# ============================================================
# Phase 4 — 파동(wave) 파라미터
# ============================================================

# 파동당 방문객. Groq rate-limit + 시차 도착 조합으로 50 정도가 압축 운영의 안정 지점.
WAVE_VISITOR_COUNT = int(os.getenv("WAVE_VISITOR_COUNT", "50"))

# 시차 도착이 펼쳐지는 시간(초) — 푸아송 도착으로 thundering herd / LLM rate-limit 완화.
WAVE_ARRIVAL_SPREAD_SEC = int(os.getenv("WAVE_ARRIVAL_SPREAD_SEC", "180"))

# /booths 폴링 주기 — 부스별 대기열 길이 시계열 (부스 불균형 지표 계산용).
WAVE_MONITOR_INTERVAL_SEC = int(os.getenv("WAVE_MONITOR_INTERVAL_SEC", "30"))

STAFF_BOOTHS = [4, 5, 6, 7, 8]  # 부스마다 staff 1명