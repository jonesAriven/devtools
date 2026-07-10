# ============================================================
# infra-monitor (基础设施监控) Dockerfile
# ============================================================
# 构建产物: target/infra-monitor.jar
# 运行: host 网络模式, 端口 8088
# ============================================================

FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/infra-monitor.jar app.jar
EXPOSE 8088

ENV TZ=Asia/Shanghai

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
