FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/seat-reservation-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]