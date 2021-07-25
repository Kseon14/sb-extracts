FROM maven:3.6-jdk-11

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

COPY . /usr/src/app
RUN mvn install -DskipTests

EXPOSE 9020

CMD ["java", "-jar", "/usr/src/app/target/sb-extracts-1.2.jar"]