# 배포 안내

`compose.yml`은 API, 알림 워커와 로그 수집기를 실행합니다. MySQL과 Redis는 별도로 준비해야 합니다. 배포 서버에는 Docker Engine과 Docker Compose v2가 필요합니다.

## 1. 외부 이미지 준비

저장소 루트에서 Dockerfile의 `api`와 `worker` 대상을 빌드하고 컨테이너 레지스트리에 게시합니다. 태그는 커밋별로 고정해 배포 이력을 추적하세요.

```sh
TAG=$(git rev-parse --short HEAD)
REGISTRY=registry.example.com/catxi

docker build --target api -t "$REGISTRY/api:$TAG" .
docker build --target worker -t "$REGISTRY/worker:$TAG" .
docker push "$REGISTRY/api:$TAG"
docker push "$REGISTRY/worker:$TAG"
```

실제 레지스트리 주소에 맞춰 `REGISTRY`를 바꾸고, 아래 환경 파일의 `API_IMAGE`와 `WORKER_IMAGE`에 게시한 태그를 지정합니다.

## 2. 서버 설정

Compose 환경 파일은 서버의 `/etc/catxi/catxi.env`에서 관리합니다. 저장소에는 올리지 마세요. `compose.yml`에 정의된 변수 목록을 기준으로 이미지, DB, Redis, 인증, CORS와 로그 설정을 채웁니다.

```sh
sudo install -d -m 700 /etc/catxi
sudo touch /etc/catxi/catxi.env
sudo chmod 600 /etc/catxi/catxi.env
```

FCM을 켜는 경우 서비스 계정 JSON도 서버에만 두고 `FCM_SERVICE_ACCOUNT_HOST_FILE`에 그 경로를 지정합니다. 워커 컨테이너에는 읽기 전용으로 연결됩니다. 운영에서는 `CATXI_AUTH_MODE=kakao`를 사용하며, `local` 로그인은 `dev` 또는 `local` 프로필에서만 허용됩니다.

MySQL과 Redis는 TLS를 포함한 사내 운영 기준에 맞춰 구성하세요. Redis Streams의 데이터 유실을 줄이려면 AOF를 켜고 메모리 부족 시 키를 임의로 제거하지 않도록 설정합니다. API와 워커의 DB 계정은 분리하고, 연결 풀 크기는 인스턴스 수와 DB 최대 연결 수를 함께 고려해 정합니다.

## 3. 배포와 확인

저장소 루트에서 실행합니다.

```sh
docker compose --env-file /etc/catxi/catxi.env -f compose.yml config --quiet
docker compose --env-file /etc/catxi/catxi.env -f compose.yml pull
docker compose --env-file /etc/catxi/catxi.env -f compose.yml up -d
docker compose --env-file /etc/catxi/catxi.env -f compose.yml ps
```

API와 워커의 준비 상태를 확인합니다.

```sh
curl --fail http://127.0.0.1:8081/actuator/health/readiness
curl --fail http://127.0.0.1:8082/actuator/health/readiness
```

API 포트는 기본적으로 로컬 인터페이스에만 열립니다. 외부 요청은 배포 환경의 프록시나 로드 밸런서를 통해 전달하고, 관리 포트는 신뢰된 내부 경로에서만 접근하도록 제한하세요.

## 데이터 변경 시 주의

API 시작 시 Liquibase가 스키마 변경을 적용합니다. 이전 버전으로 되돌려도 DB 변경은 자동으로 복구되지 않으므로, 스키마 변경 배포 전에 백업과 이전 버전 호환성을 확인하세요. FCM 실패 이력 보존 기간은 기본 180일이며 `FCM_FAILURE_RETENTION`으로 조정할 수 있습니다.

모니터링 서버 설정은 [모니터링 안내](monitoring/README.md)를 참고하세요.
