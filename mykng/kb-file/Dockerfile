# kb-file 文件微服务 Dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="kb-team"

WORKDIR /app

# jar 通过 docker-compose volume 挂载，无需 COPY（更新 jar 后 force-recreate 即可生效）
# COPY target/kb-file.jar /app/kb-file.jar

# 时区设置（直接复制时区文件，无需安装 tzdata 包）
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8082

ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/kb-file.jar"]
