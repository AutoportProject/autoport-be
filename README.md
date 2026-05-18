# Autoport Backend

GitHub 저장소를 분석하고, AI를 활용해 개발자 포트폴리오 초안을 생성/저장하는 Autoport 백엔드 API 서버입니다.

## 주요 기능

- 로컬 이메일 회원가입/로그인 및 JWT 인증
- GitHub OAuth 로그인/회원가입
- 인증된 사용자의 GitHub 저장소 목록 조회
- GitHub 저장소 README, 언어, 커밋, 활동 정보 분석
- Gemini API 기반 포트폴리오 콘텐츠 생성
- 포트폴리오 저장, 수정, 삭제, 목록/상세 조회
- Swagger UI 기반 API 문서 제공

## 기술 스택

- Java 17
- Spring Boot 4.0.5
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT
- Springdoc OpenAPI
- Google Gemini API
- Gradle
- Docker

## 프로젝트 구조

```text
src/main/java/autoport
├── ai            # Gemini 포트폴리오 생성
├── auth          # 로그인, 회원가입, 이메일 인증, GitHub OAuth
├── common        # 공통 응답, 예외 처리, 헬스체크
├── config        # Security, JWT, OpenAPI 설정
├── github        # GitHub 저장소 조회/분석
├── portfolio     # 포트폴리오 생성/저장/조회
└── user          # 사용자 조회
```

## 시작하기

### 요구사항

- JDK 17
- PostgreSQL
- Git

### 환경변수 설정

로컬 실행 시 `.env.local.example`을 참고해 환경변수를 설정합니다. 실제 키, 토큰, 운영 DB 주소, 운영 도메인은 README에 작성하지 않습니다.

```bash
cp .env.local.example .env.local
```

Spring Boot는 `.env.local` 파일을 자동으로 읽지 않으므로, IDE 실행 설정에 등록하거나 터미널에서 직접 export 해야 합니다.

```bash
export SPRING_PROFILES_ACTIVE=local
export DB_URL="your-local-database-url"
export DB_USERNAME="your-database-username"
export DB_PASSWORD="your-database-password"
export JWT_SECRET="your-jwt-secret"
export GITHUB_CLIENT_ID="your-github-client-id"
export GITHUB_CLIENT_SECRET="your-github-client-secret"
export GITHUB_REDIRECT_URI="your-github-oauth-redirect-uri"
export GEMINI_API_KEY="your-gemini-api-key"
```

이메일 인증 발송을 사용하려면 Resend 설정도 추가합니다.

```bash
export RESEND_API_KEY="your-resend-api-key"
export RESEND_FROM="your-verified-sender"
```

### 데이터베이스 준비

PostgreSQL에 로컬 DB를 생성합니다.

```bash
createdb autoport
```

초기 테이블과 기본 포트폴리오 템플릿은 `sql/schema.sql`로 준비할 수 있습니다.

```bash
psql -d autoport -f sql/schema.sql
```

애플리케이션 설정은 `spring.jpa.hibernate.ddl-auto=update`이므로, 실행 시 엔티티 기준으로 스키마가 갱신됩니다.

### 실행

```bash
./gradlew bootRun
```

서버 기본 주소는 다음과 같습니다.

```text
http://localhost:8080
```

헬스체크:

```bash
curl http://localhost:8080/health
```

### 테스트

```bash
./gradlew test
```

### 빌드

```bash
./gradlew clean build
```

## Docker 실행

이미지 빌드:

```bash
docker build -t autoport-be .
```

컨테이너 실행:

```bash
docker run --rm -p 8080:8080 --env-file .env.local autoport-be
```

## API 문서

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON:

```text
http://localhost:8080/v3/api-docs
```

## 인증

`/api/auth/**`, `/health`, `/api/health`, Swagger 관련 경로는 인증 없이 접근할 수 있습니다.

그 외 API는 JWT Bearer 토큰이 필요합니다.

```http
Authorization: Bearer <accessToken>
```

## 주요 API

### Health

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/` | 서비스 상태 확인 | 불필요 |
| GET | `/health` | 서비스 상태 확인 | 불필요 |
| GET | `/api/health` | 서비스 상태 확인 | 불필요 |

### Auth

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/email/send` | 이메일 인증 코드 발송 | 불필요 |
| POST | `/api/auth/signup` | 로컬 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로컬 로그인 | 불필요 |
| POST | `/api/auth/github` | GitHub OAuth 코드로 로그인 시도 | 불필요 |
| POST | `/api/auth/signup/github` | GitHub 신규 사용자 가입 완료 | 불필요 |
| POST | `/api/auth/github/signup` | GitHub 가입 완료 alias | 불필요 |

로컬 회원가입 예시:

```json
{
  "email": "user@example.com",
  "password": "password1234",
  "name": "홍길동",
  "bio": "백엔드 개발자",
  "code": "123456"
}
```

로컬 로그인 예시:

```json
{
  "email": "user@example.com",
  "password": "password1234"
}
```

### Users

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 내 정보 조회 | 필요 |
| GET | `/api/users` | 사용자 목록 조회 | 필요 |

사용자 목록 쿼리:

- `page`
- `size`
- `keyword`
- `provider`

### GitHub

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/github/repos` | 내 GitHub 저장소 목록 조회 | 필요 |
| POST | `/api/github/analyze` | 저장소 분석 | 필요 |

저장소 목록 쿼리:

- `sort`: `created`, `updated`, `pushed`, `full_name` 등 GitHub API 정렬 값
- `direction`: `asc` 또는 `desc`
- `page`
- `perPage`

저장소 분석 요청 예시:

```json
{
  "repoId": 123456789,
  "repoName": "autoport-be",
  "owner": "github-username"
}
```

### Portfolio

| Method | URL | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/portfolio/generate` | 분석 결과 기반 AI 포트폴리오 생성 | 필요 |
| POST | `/api/portfolio` | 포트폴리오 저장 | 필요 |
| GET | `/api/portfolio` | 내 포트폴리오 목록 조회 | 필요 |
| GET | `/api/portfolio/{portfolioId}` | 포트폴리오 상세 조회 | 필요 |
| PUT | `/api/portfolio/{portfolioId}` | 포트폴리오 수정 | 필요 |
| DELETE | `/api/portfolio/{portfolioId}` | 포트폴리오 삭제 | 필요 |

포트폴리오 생성 요청 예시:

```json
{
  "analysisResult": {
    "projectName": "autoport-be",
    "summary": "GitHub 저장소 분석 기반 포트폴리오 생성 API",
    "stacks": ["Java", "Spring Boot", "PostgreSQL"],
    "highlights": ["JWT 인증", "GitHub API 연동", "Gemini 기반 콘텐츠 생성"],
    "repoUrl": "https://github.com/example/autoport-be",
    "mainLanguage": "Java",
    "commitCount": 120
  },
  "templateId": 1,
  "userName": "홍길동",
  "bio": "백엔드 개발자",
  "tone": "professional",
  "emphasis": "인증과 API 설계 경험을 강조"
}
```

포트폴리오 저장 요청 예시:

```json
{
  "title": "홍길동의 백엔드 포트폴리오",
  "bio": "Spring Boot 기반 백엔드 개발 경험을 보유하고 있습니다.",
  "templateId": 1,
  "isPublic": true,
  "featuredProjectId": null,
  "projects": [
    {
      "repoId": 123456789,
      "name": "autoport-be",
      "description": "GitHub 저장소 분석 기반 포트폴리오 생성 API",
      "techStacks": ["Java", "Spring Boot", "PostgreSQL"],
      "highlights": ["JWT 인증", "GitHub API 연동"],
      "githubUrl": "https://github.com/example/autoport-be",
      "order": 1
    }
  ]
}
```

## 응답 형식

성공 응답은 공통 래퍼 형태로 반환됩니다.

```json
{
  "success": true,
  "data": {}
}
```

오류 응답은 공통 오류 구조를 사용합니다.

```json
{
  "code": "AUTH_005",
  "message": "Unauthorized"
}
```

## 배포 참고

- 기본 포트는 `PORT` 환경변수로 변경할 수 있으며, 기본값은 `8080`입니다.
- 서버는 `0.0.0.0`에 바인딩됩니다.
- 운영 DB, GitHub OAuth, JWT Secret, Gemini API Key, Resend API Key는 배포 환경변수로 관리합니다.
- Dockerfile은 Gradle 빌드 스테이지에서 테스트를 제외하고 JAR를 생성합니다.
