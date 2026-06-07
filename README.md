# Ryu Music — 안드로이드 로컬 음악 플레이어 (시작 프로젝트)

HTML 웹 버전을 네이티브로 옮긴 Kotlin + Jetpack Compose 프로젝트입니다.
기기에 저장된 음악 파일을 스캔해서 재생하며, 모바일 웹에서 막히던
**백그라운드 재생 / 잠금화면·알림 컨트롤**을 Media3로 해결합니다.

## 기술 스택
- Kotlin + Jetpack Compose (Material 3)
- Media3 ExoPlayer + MediaSessionService (백그라운드 재생, 미디어 알림)
- MediaStore (기기 로컬 음악 스캔)
- 최소 SDK 24, 타깃 SDK 34

## 열기 / 실행 방법
1. Android Studio (Hedgehog 이상 권장) 실행
2. **File ▸ Open** 에서 이 `MusicPlayer` 폴더 선택
3. Gradle 자동 동기화 대기 (인터넷 필요 — 의존성 다운로드)
4. 실제 기기 또는 에뮬레이터 연결 후 ▶ Run

> 에뮬레이터에는 음악 파일이 없을 수 있습니다. 테스트하려면 에뮬레이터에
> mp3를 넣거나(드래그&드롭) 실제 기기에서 실행하세요.

## 권한
앱 첫 실행 시 음악 접근 권한을 요청합니다.
- Android 13+ : `READ_MEDIA_AUDIO`
- Android 12 이하 : `READ_EXTERNAL_STORAGE`
- 알림(미디어 컨트롤 표시) : `POST_NOTIFICATIONS`

## 프로젝트 구조
```
app/src/main/
├─ AndroidManifest.xml            권한 + 서비스 등록
├─ java/com/ryu/musicplayer/
│  ├─ MainActivity.kt             권한 요청 + MediaController 연결
│  ├─ data/
│  │  ├─ Track.kt                 곡 데이터 모델
│  │  └─ MusicRepository.kt       MediaStore 조회
│  ├─ playback/
│  │  └─ PlaybackService.kt       백그라운드 재생 핵심 (MediaSessionService)
│  └─ ui/
│     ├─ PlayerViewModel.kt       재생 상태 관리
│     ├─ PlayerScreen.kt          목록 + 하단 재생바 UI
│     └─ Theme.kt
└─ res/                           아이콘, 문자열, 테마
```

## 핵심 동작 설명
- `PlaybackService`(MediaSessionService)가 포그라운드 서비스로 재생을 유지하므로
  앱을 내리거나 화면이 꺼져도 음악이 계속 재생됩니다.
- `MediaController`가 Activity ↔ Service를 연결해, UI는 재생 명령만 보내고
  실제 재생/알림은 서비스가 담당합니다.
- 잠금화면과 알림창의 재생/일시정지/이전·다음 버튼은 Media3가 자동 생성합니다.

## 다음 단계 아이디어
- 재생 진행바(SeekBar) + 현재 위치 표시
- 셔플 / 반복 모드 버튼
- 앨범 아트 표시 (MediaStore albumId → 썸네일)
- 검색 / 정렬 / 플레이리스트

## 주의
Gradle Wrapper JAR(`gradle/wrapper/gradle-wrapper.jar`)은 용량상 포함하지 않았습니다.
Android Studio가 프로젝트를 열 때 자동 생성하거나, 터미널에서
`gradle wrapper` 명령으로 만들 수 있습니다. (Android Studio로 열면 신경 쓸 필요 없음)
