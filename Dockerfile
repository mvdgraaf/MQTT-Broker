FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# We stellen een standaardwaarde in
ENV PORT=1883


COPY target/MQTTBroker-1.0.jar app.jar

# De poort die Docker naar buiten toe 'zichtbaar' maakt
EXPOSE ${PORT}

# De 'sh -c' is nodig om de variabele ${PORT} door te geven aan je Java args
# We voegen de ZGC vlaggen toe
ENTRYPOINT ["sh", "-c", "java -XX:+UseZGC -jar app.jar ${PORT} ${WS_PORT}"]