FROM eclipse-temurin:22-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
