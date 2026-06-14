FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/kb-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar", "--spring.profiles.active=prod"]
