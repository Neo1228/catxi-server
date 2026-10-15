# 배포 안내

루트 `compose.yml`은 배포 서버에서 API와 워커 이미지를 실행합니다. 두 이미지는 같은 커밋에서 빌드하고 같은 버전으로 배포합니다. MySQL과 Redis는 Compose 외부에서 운영합니다. 서비스 구성은 [아키텍처 개요](docs/architecture/overview.md)를 참고하세요.

## 준비

- Docker Engine과 Docker Compose 플러그인 v2를 준비합니다.
- API와 워커에서 접근할 MySQL 8 및 Redis를 준비합니다. API와 워커에는 별도 계정을 발급합니다.
- 배포 서버에서 Compose 환경 변수 값을 `/etc/catxi/catxi.env`에 직접 작성하고 소유자만 읽을 수 있도록 권한을 제한합니다. 필요한 변수는 루트 `compose.yml`에서 확인합니다.
- Firebase 서비스 계정 파일은 서버에 두고 `FCM_SERVICE_ACCOUNT_HOST_DIRECTORY`에 해당 디렉터리를 지정합니다. 파일은 워커에 읽기 전용으로 연결됩니다.
- 상태 점검과 메트릭 수집에 쓰는 API 및 워커 관리 포트는 사설망이나 루프백에서만 접근하도록 제한합니다.

비밀키는 32바이트 이상의 난수를 Base64로 만들어 설정합니다.

```sh
openssl rand -base64 32
```

API 계정에는 Liquibase가 데이터베이스 구조를 갱신하는 데 필요한 권한을 줍니다. 워커 계정에는 이 권한을 주지 말고, 알림 토큰과 알림 대기 요청, 실패 기록을 처리하는 권한만 부여합니다.

`DB_POOL_MAX_SIZE`와 `R2DBC_POOL_MAX_SIZE`는 API와 워커가 각자 사용할 수 있는 데이터베이스 연결 수를 정합니다. 인스턴스 수를 반영해 MySQL 전체 연결 수를 계산하고 운영 작업을 위한 여유를 남깁니다. `FCM_STREAM_CONCURRENCY`도 워커 인스턴스마다 적용되므로 데이터베이스 연결 수와 Firebase 처리 한도를 함께 고려합니다.

## 이미지 배포

같은 커밋 SHA를 두 이미지에 사용합니다.

```sh
docker build --target api -t ghcr.io/your-org/catxi-api:<commit-sha> .
docker build --target worker -t ghcr.io/your-org/catxi-worker:<commit-sha> .
```

이미지를 레지스트리에 게시한 뒤 서버의 `/etc/catxi/catxi.env`에 해당 이미지 주소를 지정합니다. 매칭 알림을 사용할 때는 워커 프로필을 함께 켭니다.

```sh
docker compose --env-file /etc/catxi/catxi.env --profile worker -f compose.yml config --quiet
docker compose --env-file /etc/catxi/catxi.env --profile worker -f compose.yml pull
docker compose --env-file /etc/catxi/catxi.env --profile worker -f compose.yml up -d api worker
docker compose --env-file /etc/catxi/catxi.env --profile worker -f compose.yml ps
```

운영 스키마 변경이 포함된 배포는 데이터베이스 백업과 변경 내역을 먼저 확인하고, API와 워커가 함께 호환되는 순서로 교체합니다. 워커를 추가로 운영할 때는 같은 이미지 버전과 Redis Stream 그룹을 사용하고 각 인스턴스의 관리 주소를 모니터링 대상에 등록합니다.

로그를 별도 Loki로 전송하려면 `LOKI_WRITE_URL`, `LOKI_WRITE_USERNAME`, `LOKI_WRITE_PASSWORD`를 설정하고 `log-shipping` 프로필을 사용합니다. 해당 관리 및 메트릭 포트는 공개 인터넷에 열지 않습니다.

## 인증과 선택 기능

운영은 `CATXI_AUTH_MODE=kakao`로 설정합니다. `CATXI_AUTH_MODE=local`은 개발 프로필에서만 사용할 수 있으며 운영 프로필에서 선택하면 애플리케이션이 시작되지 않습니다. 로컬 이메일 가입과 로그인 경로는 `POST /api/members/signUp`, `POST /api/members/login`이며 기존 회원 API에서 제공합니다.

매칭 푸시를 사용할 때는 API와 워커 모두에 `FCM_NOTIFICATIONS_ENABLED=true`를 설정하고 Firebase 서비스 계정을 워커에 연결합니다. Discord 신고 알림은 `DISCORD_REPORTS_ENABLED=true`와 웹훅 주소를 설정해 사용할 수 있습니다. 신고는 먼저 데이터베이스에 저장한 뒤 알림을 보내며, Discord 장애가 신고 저장을 취소하지는 않습니다.

## 데이터 보존

전송을 마친 알림 요청은 기본 30일 뒤 정리합니다. 전송에 계속 실패한 알림과 실패 내역은 `FCM_FAILURE_RETENTION` 기본값인 180일 동안 보관합니다. 보관 기간은 운영 정책과 디스크 여유를 고려해 조정합니다.

채팅 이력, 신고, 회원 데이터는 별도의 데이터베이스 백업 정책으로 보호합니다. 백업 파일의 복구 가능 여부를 정기적으로 확인하세요.
