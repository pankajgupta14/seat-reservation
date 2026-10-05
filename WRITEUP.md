\# Seat Reservation at Scale — Write-up



\## 1. Atomic Reservation Decision



\### Mechanism



The reservation operation is protected by a \*\*database transaction with pessimistic row-level locking using MySQL `SELECT ... FOR UPDATE`\*\*.



When a reservation request arrives:



1\. The request is executed inside a transaction.

2\. The requested seats are sorted into a deterministic order before locking.

3\. Each requested seat is selected using:



```sql

SELECT id

FROM show\_seats

WHERE show\_id = ?

&#x20; AND seat\_number = ?

FOR UPDATE;

```



4\. The transaction verifies that every requested seat is `AVAILABLE`.

5\. The reservation is created.

6\. The seats are changed from `AVAILABLE` to `CONFIRMED`.

7\. The idempotency record is completed with the reservation ID.

8\. If any step fails, the transaction rolls back.



\### Why is it race-free?



The important part is the database row lock.



Suppose two users try to reserve the same seat at exactly the same time.



The first transaction obtains the row lock using `SELECT ... FOR UPDATE`. The second transaction must wait for that lock to be released.



After the first transaction commits, the second transaction continues and sees that the seat is no longer `AVAILABLE`. It therefore fails with a seat-conflict response instead of creating another reservation.



This means the correctness does not depend on Java `synchronized` or the number of application instances. The database is the source of truth for seat ownership.



The final seat update also contains an availability condition:



```sql

UPDATE show\_seats

SET status = 'CONFIRMED',

&#x20;   reservation\_id = ?

WHERE id = ?

&#x20; AND status = 'AVAILABLE';

```



The update must affect exactly one row. Otherwise the reservation is rejected.



\### Multi-seat reservations and deadlocks



For a reservation containing multiple seats, seats are locked in a \*\*deterministic order\*\*.



For example, if one request wants:



```text

A1, A2, A3

```



and another request wants:



```text

A2, A3, A4

```



both requests acquire locks according to the same ordering rule rather than in arbitrary request order.



This reduces the possibility of a circular wait and therefore reduces deadlock risk.



If a database transaction still encounters a deadlock under extreme contention, the transaction is rolled back and the operation can be retried at the application/API layer.



\### Money representation



All monetary values are represented as \*\*integer paise\*\*, not floating-point values.



For example:



```text

₹250.00 = 25000 paise

```



The reservation amount is calculated using integer arithmetic:



```java

long amountPaise =

&#x20;       Math.multiplyExact(show.getPricePaise(), seats.size());

```



This avoids floating-point rounding problems.







\## 2. Idempotency



\### Where the idempotency key is stored



The client sends an `Idempotency-Key` with the reservation request.



The key is stored in the MySQL `idempotency\_keys` table along with:



\- `user\_id`

\- `show\_id`

\- `idempotency\_key`

\- `request\_hash`

\- `reservation\_id`

\- `created\_at`



The database has a uniqueness constraint for the logical request identity, preventing multiple idempotency records from being created for the same user, show and key.



\### How exactly-once processing is enforced



The reservation flow first creates or retrieves the idempotency record.



If the request has already completed and the record contains a `reservation\_id`, the existing reservation is returned instead of creating another reservation.



Therefore, retrying the same request with the same idempotency key does not create another reservation.



For example:



```text

Request 1:

Idempotency-Key: abc123

&#x20;      ↓

Create reservation: R1



Request 2:

Idempotency-Key: abc123

&#x20;      ↓

Existing reservation found

&#x20;      ↓

Return R1

```



In testing, 100 requests using the same idempotency key returned the same reservation ID, with no duplicate reservation created.



\### Same key with a different request body



The request body is converted into a deterministic request hash.



The stored hash is compared with the hash of a retry using the same idempotency key.



If the same key is reused with different reservation parameters, the hashes do not match and the request is rejected with an idempotency conflict.



For example:



```text

First request:

Key = abc123

Seats = A1,A2

Hash = H1



Second request:

Key = abc123

Seats = B1,B2

Hash = H2

```



Because `H1 != H2`, the second request is rejected.



This prevents a client from accidentally reusing an idempotency key for a different logical operation.



\### Why this matters



Idempotency protects against retries caused by network failures, client retries, timeouts or duplicate submissions.



Concurrency control and idempotency solve different problems:



\- \*\*Concurrency control\*\* prevents two different requests from successfully booking the same seat.

\- \*\*Idempotency\*\* prevents retries of the same logical request from creating duplicate reservations.





\## 3. Holds \& Expiry



The current implementation uses a direct reservation flow where seats move from `AVAILABLE` to `CONFIRMED` within the same database transaction.



A temporary payment hold/expiry workflow is not currently implemented because the assignment's core focus was safe reservation under concurrency and idempotent retries.



\### Production approach



If temporary seat holds were required, I would introduce an explicit state such as:



```text

AVAILABLE → HELD → CONFIRMED

&#x20;                ↓

&#x20;             EXPIRED

&#x20;                ↓

&#x20;             AVAILABLE

```



A hold would contain:



\- `hold\_id`

\- `show\_id`

\- `seat\_id`

\- `user\_id`

\- `status`

\- `expires\_at`

\- `created\_at`



The seat row would still be protected using database locking when creating or modifying a hold.



\### Expiry



A scheduled cleanup process would periodically find expired holds:



```sql

SELECT ...

FROM show\_seats

WHERE status = 'HELD'

&#x20; AND hold\_expires\_at < NOW();

```



Expired holds would then be changed back to `AVAILABLE` inside a transaction.



The expiry operation would also use row-level locking so that an expiry process cannot race with a payment confirmation or another reservation operation.



\### Why not use an in-memory timer?



An in-memory timer would not be sufficient in a multi-instance deployment because another application instance may not know about the hold.



The database should remain the source of truth for seat ownership and expiry state.



For a production implementation, I would also make the expiry operation idempotent so that running the cleanup multiple times does not produce an inconsistent state.





\## 4. Consistency vs Availability Under a Partition



For seat reservation, I prioritize \*\*consistency over availability\*\*.



The most important invariant is:



> A seat must never be successfully sold to two different users.



During a network partition or database connectivity problem, allowing reservations to continue independently on different application instances could result in conflicting seat ownership.



Therefore, if the application cannot safely access the authoritative database state, the reservation operation should fail rather than accept a booking that cannot be durably committed.



\### Why consistency is more important here



For a seat-booking system:



```text

Double booking = incorrect business state

Temporary booking failure = recoverable

```



A user can retry a failed reservation, but recovering from two customers being successfully charged or assigned the same seat is significantly more difficult.



The database therefore acts as the source of truth for:



\- Seat availability

\- Reservation ownership

\- Idempotency records

\- Reservation state



\### Expected behavior during a database/network failure



If the application cannot reach the database or cannot obtain the required database lock:



1\. Do not confirm the reservation.

2\. Do not report the seat as successfully booked.

3\. Return an appropriate error to the client.

4\. Log the failure with the correlation ID.

5\. Allow the client to retry safely using the same idempotency key.



This sacrifices some availability during a partition, but preserves the correctness of seat ownership.





\## 5. Observability



The service includes health checks, application logs, correlation IDs and application metrics.



\### Logs



Each request can be traced using a correlation ID. Important reservation events are logged, including declined reservations and the reason for rejection.



Examples of useful events include:



\- Reservation successfully created

\- Seat already taken

\- Booking limit exceeded

\- Idempotency conflict

\- Database or transaction failure



The correlation ID makes it possible to trace a request across the application logs.



\### Metrics



Spring Boot Actuator is enabled for operational health and metrics.



The service exposes health information through:



```text id="o0yq9h"

GET /actuator/health

```



The health endpoint is used to determine whether the application is running correctly.



Application-level reservation metrics are also recorded for outcomes such as successful reservations and declined requests.



\### What would page me at 2 AM?



In production, I would configure alerts for:



1\. \*\*High 5xx error rate\*\*  

&#x20;  Indicates application or infrastructure failures.



2\. \*\*Database connectivity failures\*\*  

&#x20;  The reservation service cannot safely process bookings without the database.



3\. \*\*High reservation latency\*\*  

&#x20;  Could indicate database contention, slow queries or lock contention.



4\. \*\*Large increase in seat conflicts\*\*  

&#x20;  Could indicate unusual traffic or contention on popular shows.



5\. \*\*Idempotency conflict spike\*\*  

&#x20;  Could indicate a client integration problem repeatedly reusing keys incorrectly.



6\. \*\*Application instance unavailable\*\*  

&#x20;  Indicates reduced service capacity or a deployment/infrastructure problem.



7\. \*\*Database lock/deadlock increase\*\*  

&#x20;  Indicates contention that may require investigation or query/transaction optimization.



The goal is not simply to collect logs, but to detect failures that affect correctness, availability or customer experience.





\## 6. AI Usage



AI tools were used during development as a \*\*development assistant\*\*, not as a replacement for understanding or making the core engineering decisions.



\### Directed by AI



AI assistance was used for:



\- Reviewing implementation approaches and identifying potential race conditions.

\- Explaining Java, Spring Boot, JDBC and database-concurrency concepts.

\- Reviewing error messages and helping troubleshoot development issues.

\- Suggesting improvements to documentation and README structure.

\- Helping design and refine test scenarios for concurrency and idempotency.

\- Reviewing code for potential edge cases.



\### Decided by me



The important architectural decisions were understood and validated by me, including:



\- Using MySQL as the source of truth for seat state.

\- Using transactional processing for reservation consistency.

\- Using `SELECT ... FOR UPDATE` for pessimistic row-level locking.

\- Locking multiple seats in deterministic order to reduce deadlock risk.

\- Using an idempotency key stored in the database.

\- Storing a request hash to detect same-key/different-body requests.

\- Representing money as integer paise rather than floating point.

\- Returning a successful existing reservation for a completed idempotent retry.

\- Rejecting the reservation when the database cannot safely establish the required state.



I can explain these decisions and the concurrency/idempotency behavior independently and extend the implementation during an interview.





\## 7. What I Would Do Next



If this service were being taken from an assignment to a production system, I would improve it in the following areas.



\### 1. Implement temporary seat holds



Introduce `HELD` seats with an expiry time so users can temporarily reserve seats while completing payment.



\### 2. Add payment integration



Integrate a payment provider using an idempotent payment flow so that payment retries cannot result in duplicate charges.



Money would continue to be represented as integer paise.



\### 3. Improve observability



Add production dashboards and alerts for:



\- Reservation success/failure rate

\- p95/p99 reservation latency

\- Database lock contention

\- Deadlocks

\- Database connection pool usage

\- Idempotency conflicts

\- Seat availability

\- Payment failures



\### 4. Distributed deployment



Run multiple application instances behind a load balancer.



The database would remain the authoritative source for seat ownership, while the application remains stateless.



\### 5. Stronger automated concurrency testing



Add automated load tests covering:



\- Many users competing for one seat

\- Multiple users reserving overlapping seat sets

\- Same request retried concurrently

\- Same idempotency key with different request bodies

\- Cancellation racing with reservation

\- Database/deadlock failure scenarios



\### 6. Hold-expiry reliability



For a larger deployment, use a reliable scheduled/background processing mechanism for expired holds rather than relying only on an application-local timer.



\### 7. Database scalability



If traffic becomes very high, I would investigate database partitioning/sharding strategies, read replicas for read-heavy endpoints, connection-pool tuning and query/index optimization while keeping the seat-allocation write path strongly consistent.



The primary principle would remain unchanged:



> Never sacrifice seat-ownership correctness for availability.

