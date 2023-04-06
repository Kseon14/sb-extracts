FROM openjdk:11.0.11-jre-slim

RUN mkdir -p /usr/app
WORKDIR /usr/app

COPY target/sb-extracts-1.2.jar /usr/app
COPY .envVariable /usr/app

CMD ["bash", "-c", "source /usr/app/.envVariable && java -jar /usr/app/sb-extracts-1.2.jar"]