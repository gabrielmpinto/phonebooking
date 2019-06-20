# Application

Runs by default on port 8080. You should be redirected to the login to the login page when accessing it through your browser.

# Building

To package the application:
```
./mvnw clean install
```

To run the application:
```
./mvnw clean compile exec:java
```

To build the container image:
```
docker build -t <name> .
```

To run the container image:
```
docker run -p 8080:8080 <name>
```
