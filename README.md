# Catxi 백엔드

가톨릭대학교 학생들이 함께 택시를 탈 수 있도록 매칭, 채팅, 위치 공유와 푸시 알림을 제공하는 백엔드 서비스입니다.

## 모듈

| 모듈 | 역할 |
| --- | --- |
| `module-api` | REST API, WebSocket, 회원, 매칭, 채팅, 위치 공유와 알림 요청 처리 |
| `module-worker` | 매칭 알림 전송, 재시도와 실패 내역 관리 |
| `module-contract` | API와 워커가 함께 쓰는 알림 이벤트와 채팅 접속 키 계약 |

API의 패키지 루트는 `com.catxi`이며, `auth`, `chat`, `member`, `notification`, `report`를 기능별로 나눕니다. 각 기능 안에서 컨트롤러, DTO, 서비스, 도메인, 저장소를 구분합니다. 워커는 `com.catxi.worker.notification` 아래에서 알림 전송, 스트림 처리, 아웃박스, 실패 재처리를 담당합니다. `module-contract`에는 API와 워커가 함께 사용하는 알림 이벤트와 채팅 접속 키만 둡니다. 두 실행 모듈은 서로 의존하지 않습니다.

API 서버는 사용자 요청을 처리하고, 알림 워커는 푸시 전송과 재시도를 맡습니다. 두 서비스는 같은 저장소에서 빌드하지만 별도로 실행합니다. 요청과 알림의 흐름은 [아키텍처 개요](docs/architecture/overview.md), 서버 배포 방법은 [배포 안내](DEPLOYMENT.md)에서 확인할 수 있습니다.

## 로컬 개발

Java 21, MySQL 8, Redis 7이 필요합니다. `catxi` 데이터베이스를 만들고 `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PW`, `REDIS_HOST`, `REDIS_PORT`, `SECRET_KEY`를 셸 환경 변수로 지정합니다. `SECRET_KEY`에는 32바이트 이상의 난수를 Base64로 인코딩한 값을 사용합니다. 실제 비밀번호와 키는 저장소에 올리지 않습니다.

```sh
SPRING_PROFILES_ACTIVE=dev CATXI_AUTH_MODE=local ./gradlew :module-api:bootRun
```

개발 프로필을 실행하면 Liquibase가 데이터베이스 구조를 적용합니다. 이메일 가입과 로그인은 기존 회원 API인 `POST /api/members/signUp`과 `POST /api/members/login`을 사용합니다. 개발 환경에서 카카오 인증을 확인하려면 `CATXI_AUTH_MODE=kakao`와 카카오 설정을 지정합니다. 운영 프로필에서는 로컬 인증을 사용할 수 없습니다.

API를 먼저 실행해 스키마를 적용한 뒤 워커를 시작합니다. 워커용 DB 계정은 `WORKER_DB_USER`와 `WORKER_DB_PW`로 지정합니다. 로컬 DB가 TLS를 사용하지 않을 때는 API와 워커가 같은 `DB_SSL_MODE=DISABLED` 설정을 사용하고, 워커 포트를 API와 다르게 지정합니다.

```sh
DB_SSL_MODE=DISABLED SERVER_PORT=8082 MANAGEMENT_SERVER_PORT=8084 ./gradlew :module-worker:bootRun
```

API 서버가 요청을 받을 준비가 됐는지는 `http://localhost:8081/actuator/health/readiness`, 워커가 알림을 처리할 준비가 됐는지는 `http://localhost:8084/actuator/health/readiness`에서 확인합니다.

루트 `compose.yml`은 배포 서버에서 API 서버와 알림 워커를 실행합니다. MySQL과 Redis는 이 파일에서 실행하지 않으며, 별도로 운영 중인 서비스에 연결합니다. 로컬 개발 환경에서 데이터베이스와 Redis를 자동으로 띄우는 구성은 포함하지 않습니다.

## 빌드

API와 워커 실행 파일을 함께 빌드합니다.

```sh
./gradlew :module-api:bootJar :module-worker:bootJar
```

모니터링 구성은 별도 [모니터링 안내](monitoring/README.md)를 참고하세요.
