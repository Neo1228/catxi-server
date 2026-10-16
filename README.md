# Catxi 백엔드

가톨릭대학교 학생을 위한 택시 매칭, 채팅, 위치 공유와 푸시 알림 서비스입니다.

## 구성

| 실행 모듈 | 담당 |
| --- | --- |
| `module-api` | REST API, WebSocket, 회원, 매칭, 채팅, 위치 공유와 알림 요청 |
| `module-worker` | 푸시 알림 전달, 재시도와 실패 이력 관리 |

두 모듈은 각각 배포하고 확장할 수 있습니다. 상세한 데이터 흐름은 [아키텍처 개요](docs/architecture/overview.md)를 참고하세요.

## 로컬 실행

JDK 21, MySQL 8, Redis가 필요합니다. `catxi` 데이터베이스와 Redis를 준비하고, API에는 `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PW`, `REDIS_HOST`, `REDIS_PORT`, `SECRET_KEY`를 설정하세요. 워커에는 `WORKER_DB_USER`, `WORKER_DB_PW`, `WORKER_REDIS_USER`, `WORKER_REDIS_PW`를 별도로 설정합니다.

### API

```sh
SPRING_PROFILES_ACTIVE=dev CATXI_AUTH_MODE=local ./gradlew :module-api:bootRun
```

`CATXI_AUTH_MODE=local`은 `dev` 또는 `local` 프로필에서만 사용할 수 있습니다. 카카오 로그인을 확인할 때는 `CATXI_AUTH_MODE=kakao`와 카카오 설정을 지정하세요.

### 알림 워커

별도 터미널에서 실행합니다.

```sh
DB_SSL_MODE=DISABLED SERVER_PORT=8082 MANAGEMENT_SERVER_PORT=8084 ./gradlew :module-worker:bootRun
```

API와 워커의 준비 상태는 각각 `http://localhost:8081/actuator/health/readiness`와 `http://localhost:8084/actuator/health/readiness`에서 확인할 수 있습니다.

## 빌드와 배포

```sh
./gradlew :module-api:bootJar :module-worker:bootJar
```

운영 배포는 [배포 안내](DEPLOYMENT.md), 메트릭과 로그 구성은 [모니터링 안내](monitoring/README.md)를 참고하세요.
