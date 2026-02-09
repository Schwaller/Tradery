FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY rendezvous.jar /app/rendezvous.jar

EXPOSE 7480

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://localhost:7480/health || exit 1

ENTRYPOINT ["java", "-jar", "rendezvous.jar"]
