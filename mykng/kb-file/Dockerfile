# kb-file 文件微服务 Dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="kb-team"

WORKDIR /app

# 复制构建产物
COPY target/kb-file.jar /app/kb-file.jar

# 时区设置（使用阿里云alpine源）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

EXPOSE 8082

ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/kb-file.jar"]
