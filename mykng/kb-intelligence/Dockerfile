# kb-intelligence 知识引擎微服务 Dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="kb-team"

WORKDIR /app

COPY target/kb-intelligence.jar /app/kb-intelligence.jar

# 时区设置（直接复制时区文件，无需安装 tzdata 包）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8086

ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/kb-intelligence.jar"]
