# 1단계: 빌드 환경 (JDK 21)
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Gradle 래퍼 및 빌드 설정 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew

# 소스 코드 복사 및 애플리케이션 빌드
COPY src src
RUN ./gradlew clean bootJar -x test

# 2단계: 실행 환경 (가벼운 JRE 21)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
