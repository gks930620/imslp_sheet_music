# ===== Stage 1: 빌드 스테이지 =====
FROM gradle:8-jdk17 AS builder

WORKDIR /build

# 프론트엔드 빌드를 위한 Node.js 20 설치 (gradle build 시 npm ci && npm run build 실행됨)
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

# Gradle 파일 먼저 복사 (캐싱 활용)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 먼저 다운로드 (캐싱)
RUN gradle dependencies --no-daemon || true

# 소스코드 복사 및 빌드
# (gradle bootJar → processResources → frontendInstall(npm ci) → frontendBuild(npm run build) → dist를 static으로 복사)
COPY frontend ./frontend
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ===== Stage 2: 실행 스테이지 =====
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 시간대 설정 및 curl 설치 (헬스체크용)
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 업로드 디렉토리 생성
RUN mkdir -p /app/uploads

# JAR 파일 복사
COPY --from=builder /build/build/libs/*.jar app.jar

# 컨테이너가 사용할 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/healthz || exit 1

# 애플리케이션 실행 (프로덕션 프로파일 사용)
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
