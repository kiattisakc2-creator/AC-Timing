# Use Amazon Corretto 17 as the base image for the build stage
FROM amazoncorretto:17 AS build

# Set the working directory inside the container
WORKDIR /racetimingms

# Copy the Gradle Wrapper and the necessary build files
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy the source code into the container
COPY src src

# Make the Gradle Wrapper executable
RUN chmod +x gradlew

# Build the application
RUN ./gradlew build --no-daemon

# Use a smaller image to run the application
FROM amazoncorretto:17

# Set the working directory for the runtime stage
WORKDIR /racetimingms

# Copy the built .jar file from the build stage
COPY --from=build /racetimingms/build/libs/racetimingms-0.0.1-SNAPSHOT.war racetimingms.war

# Expose the port your application will run on
EXPOSE 8080

# Command to run the application
CMD ["java", "-jar", "racetimingms.war"]
