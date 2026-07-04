# portal-server 门户系统 Dockerfile
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="kb-team"

WORKDIR /app

COPY target/portal-server.jar /app/portal-server.jar

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

EXPOSE 8087

ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/portal-server.jar"]
