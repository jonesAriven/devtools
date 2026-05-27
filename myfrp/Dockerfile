FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY target/frp-manager-1.0.0.jar app.jar

EXPOSE 18082

ENTRYPOINT ["java", "-jar", "app.jar"]
