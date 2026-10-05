\# Seat Reservation Service



A scalable and concurrency-safe seat reservation backend built with \*\*Java, Spring Boot, MySQL, JWT, Docker, and Spring Boot Actuator\*\*.



The system is designed to handle concurrent reservation requests while ensuring:



\- A seat cannot be sold twice

\- A user cannot exceed the booking limit

\- Retried requests do not create duplicate reservations

\- Reservation operations are protected against race conditions

\- The application is observable through health checks and metrics



\## Tech Stack



\- Java

\- Spring Boot

\- Spring Data JPA / Hibernate

\- MySQL

\- Spring Security

\- JWT / Bearer Token authentication

\- REST APIs

\- Maven

\- Docker / Docker Compose

\- Spring Boot Actuator

\- Prometheus metrics



\## Architecture



```text

Client

&#x20; |

&#x20; | HTTP / REST

&#x20; v

Spring Boot Application

&#x20; |

&#x20; +----------------------+

&#x20; |                      |

&#x20; v                      v

Security Layer       Reservation API

JWT Authentication       |

&#x20;                         v

&#x20;                  Reservation Service

&#x20;                         |

&#x20;             +-----------+-----------+

&#x20;             |                       |

&#x20;             v                       v

&#x20;       Idempotency              Concurrency

&#x20;         Handling                 Control

&#x20;             |                       |

&#x20;             +-----------+-----------+

&#x20;                         |

&#x20;                         v

&#x20;                      MySQL

```



\## Core Features



\### 1. Seat Reservation



Clients can reserve an available seat through the REST API.



The reservation flow validates:



\- Authentication

\- Show existence

\- Seat availability

\- User booking limit

\- Idempotency key

\- Concurrent access



\### 2. Idempotency



The API supports an `Idempotency-Key` header.



Example:



```http

Idempotency-Key: ABC123

```



If the same request is retried multiple times with the same key, the system returns the existing reservation instead of creating another reservation.



This protects against:



\- Client retries

\- Network retries

\- Duplicate requests

\- Payment/request retry scenarios



\### 3. Concurrency Control



Multiple users may attempt to reserve the same seat at the same time.



The application uses database-level locking and transactional processing to ensure that only one request can successfully reserve the seat.



Expected behavior:



```text

100 concurrent requests

&#x20;       |

&#x20;       v

&#x20;    Same seat

&#x20;       |

&#x20;       +----> 1 successful reservation

&#x20;       |

&#x20;       +----> Remaining requests rejected

```



\### 4. Booking Limit



The system prevents a user from exceeding the configured maximum number of reservations.



\### 5. JWT Authentication



Protected APIs require a Bearer token.



Example:



```http

Authorization: Bearer <JWT\_TOKEN>

```



Requests without valid authentication are rejected.



\### 6. Observability



Spring Boot Actuator is enabled for health monitoring.



Health endpoint:



```http

GET /actuator/health

```



Example:



```json

{

&#x20; "status": "UP"

}

```



The application also exposes Prometheus-compatible metrics.



\## API Endpoints



\### Create Show



```http

POST /shows

```



Creates a show and its associated seats.



\### Get Show



```http

GET /shows/{showId}

```



Returns show information and seat state.



\### Reserve Seat



```http

POST /reservations

```



Required headers:



```http

Authorization: Bearer <JWT\_TOKEN>

Idempotency-Key: <UNIQUE\_KEY>

Content-Type: application/json

```



Example request:



```json

{

&#x20; "showId": 1,

&#x20; "seatId": 10

}

```



\### Cancel Reservation



```http

POST /reservations/{reservationId}/cancel

```



The reservation owner can cancel their reservation according to the configured business rules.



\## Database



The application uses MySQL.



Main entities include:



\- `shows`

\- `show\_seats`

\- `reservations`

\- `idempotency\_keys`

\- `booking\_locks`



The database maintains the state required to guarantee reservation consistency.



\## Idempotency Test



The system was tested with \*\*100 requests using the same idempotency key\*\*.



Result:



```text

========== IDEMPOTENCY TEST ==========



Total requests : 100

Successful     : 100

409 Conflict   : 0

5xx Errors     : 0

Other Errors   : 0



Same reservation ID : true



Reservation ID:

2a32a7ab-3b37-4e88-bbc2-ca3f18d58a47



======================================

```



This demonstrates that repeated requests for the same logical operation return the same reservation instead of creating duplicate reservations.



\## Running Locally



\### Prerequisites



Install:



\- Java

\- Maven

\- MySQL



Optional:



\- Docker

\- Docker Compose



\### Configure Database



Create the database:



```sql

CREATE DATABASE seat\_reservation;

```



Configure the database connection using environment variables:



```text

DB\_USERNAME

DB\_PASSWORD

```



The application also provides local defaults for development.



\### Start the Application



Using Maven:



```powershell

.\\mvnw.cmd spring-boot:run

```



The application runs on:



```text

http://localhost:8080

```



\### Health Check



```powershell

curl.exe http://localhost:8080/actuator/health

```



Expected:



```json

{

&#x20; "status": "UP"

}

```



\## Docker



Build the application:



```powershell

docker compose build

```



Start the services:



```powershell

docker compose up

```



Stop the services:



```powershell

docker compose down

```



\## Project Structure



```text

src/

&#x20;└── main/

&#x20;    ├── java/

&#x20;    │   └── com/paytm/seatreservation/

&#x20;    │       ├── config/

&#x20;    │       ├── controller/

&#x20;    │       ├── dto/

&#x20;    │       ├── entity/

&#x20;    │       ├── enums/

&#x20;    │       ├── exception/

&#x20;    │       ├── repository/

&#x20;    │       ├── service/

&#x20;    │       └── util/

&#x20;    │

&#x20;    └── resources/

&#x20;        └── application.properties



Dockerfile

docker-compose.yml

pom.xml

```



\## Design Decisions



\### Why Idempotency?



Network failures can cause clients to retry requests. Without idempotency, a retry could create duplicate reservations or duplicate payment operations.



The idempotency key allows the server to recognize that multiple requests represent the same logical operation.



\### Why Database Locking?



Two different users can send reservation requests for the same seat simultaneously.



Application-level checks alone are not sufficient because both requests may read the seat as available before either transaction updates it.



Database-level locking provides safe serialization of conflicting operations.



\### Why Transactions?



Reservation operations modify multiple pieces of state.



Transactions ensure that related database changes succeed or fail together and prevent partially completed reservation operations.



\## Error Handling



Typical responses include:



```text

200 / 201  Successful operation

400         Invalid request

401         Unauthorized

403         Forbidden

404         Resource not found

409         Conflict / seat already reserved / idempotency conflict

500         Internal server error

```



\## Future Improvements



Potential production improvements include:



\- Redis-based distributed locking

\- Kafka event publishing

\- Payment service integration

\- Distributed tracing

\- Rate limiting

\- Circuit breakers

\- Kubernetes deployment

\- Horizontal scaling

\- Centralized logging

\- Prometheus + Grafana dashboards



\## Author



\*\*Pankaj Gupta\*\*



Java Backend Developer



Skills:



Java | Spring Boot | Microservices | REST APIs | MySQL | Docker | Kafka | Redis

