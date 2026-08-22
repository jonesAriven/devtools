# 用 DaoCloud 镜像源，无需登录
FROM eclipse-temurin:21-jre-jammy
WORKDIR /activecode
COPY target/activation-code-server-1.0.0.jar activation-code-server-1.0.0.jar
EXPOSE 8080

# 设置容器时区为上海时间
ENV TZ=Asia/Shanghai
# 数据库连接由 docker-compose.yml 的 environment 注入，不在此硬编码

ENTRYPOINT ["java", "-jar", "activation-code-server-1.0.0.jar"]
