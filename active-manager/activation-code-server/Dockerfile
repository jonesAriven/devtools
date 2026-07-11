# 用 DaoCloud 镜像源，无需登录
FROM eclipse-temurin:21-jre-jammy
WORKDIR /activecode
COPY target/activation-code-server-1.0.0.jar activation-code-server-1.0.0.jar
EXPOSE 8080

# 设置容器时区为上海时间
ENV TZ=Asia/Shanghai

# 数据库连接默认指向宿主机MySQL（Docker内通过host.docker.internal访问宿主机）
ENV SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/tools?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
ENV SPRING_DATASOURCE_USERNAME=tools
ENV SPRING_DATASOURCE_PASSWORD=toolsmarschat

ENTRYPOINT ["java", "-jar", "activation-code-server-1.0.0.jar"]
