# GharFix

GharFix is a full-stack home services booking platform where users can browse service 
categories, find and book verified workers (electricians, plumbers, cleaners, etc.), 
manage their bookings, and leave reviews. Built as a backend-focused project to 
demonstrate REST API design, authentication, and layered Spring Boot architecture.

## Tech Stack

- **Java 17**
- **Spring Boot 3.4.3** — Web, Validation
- **Spring Security** — session-based authentication
- **Spring Data JPA / Hibernate** — ORM
- **Thymeleaf** — server-rendered views
- **H2** (local dev, file-based) / **PostgreSQL** (production)
- **Docker** — multi-stage build for deployment
- **Maven** — build tool

## Features

- User registration and login
- Browse service categories and workers
- Search workers by service/query
- Book a service and view your booking history
- Reviews display on the homepage

## Running Locally

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:5000` by default (H2 file-based DB, zero config needed).

## Deployment

Includes a `Dockerfile` (multi-stage build) and `render.yaml` for deployment to Render 
using PostgreSQL in production (`SPRING_PROFILES_ACTIVE=prod`).

## Project Structure

```
src/main/java/com/gharfix/
├── controller/   # Web + REST controllers
├── service/      # Business logic
├── repository/   # Spring Data JPA repositories
├── entity/       # JPA entities
├── dto/          # Request/response DTOs
├── security/     # Spring Security config, custom UserDetails
└── config/       # App-level configuration
```
