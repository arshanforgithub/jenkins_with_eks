FROM eclipse-temurin:17-jre
COPY target/jenkins-eks-demo.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
