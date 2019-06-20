FROM openjdk:11.0.3-jdk-stretch

ENV VERTICLE_FILE phone-booking-1.0.0-SNAPSHOT-fat.jar
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $VERTICLE_FILE"]
