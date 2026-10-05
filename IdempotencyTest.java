import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class IdempotencyTest {

   private static final String BASE_URL = "http://localhost:8081";
private static final int SHOW_ID = 3;
private static final String SEAT = "B2";
    private static final int TOTAL_REQUESTS = 100;

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newHttpClient();

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        List<Future<HttpResponse<String>>> futures =
                new ArrayList<>();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {

            futures.add(
                executor.submit(
                    () -> sendRequest(client)
                )
            );
        }

        int success = 0;
        int conflict = 0;
        int serverErrors = 0;
        int otherErrors = 0;

        String reservationId = null;
        boolean sameReservation = true;

        for (Future<HttpResponse<String>> future : futures) {

            try {

                HttpResponse<String> response = future.get();

               if (response.statusCode() == 200 ||
    response.statusCode() == 201) {
                    success++;

                    String body = response.body();

                    String currentId =
                            extractReservationId(body);

                    if (reservationId == null) {
                        reservationId = currentId;
                    } else if (!reservationId.equals(currentId)) {
                        sameReservation = false;
                    }

                } else if (response.statusCode() == 409) {
                    conflict++;

                } else if (response.statusCode() >= 500) {
                    serverErrors++;

                } else {
                    otherErrors++;
                }

            } catch (Exception e) {
                otherErrors++;
            }
        }

        executor.close();

        System.out.println();
        System.out.println("========== IDEMPOTENCY TEST ==========");
        System.out.println("Total requests : " + TOTAL_REQUESTS);
        System.out.println("Successful     : " + success);
        System.out.println("409 Conflict   : " + conflict);
        System.out.println("5xx Errors     : " + serverErrors);
        System.out.println("Other Errors   : " + otherErrors);
        System.out.println("Same reservation ID : " + sameReservation);
        System.out.println("Reservation ID      : " + reservationId);
        System.out.println("======================================");
    }

    private static HttpResponse<String> sendRequest(
            HttpClient client) throws Exception {

        String json = """
                {
                    "seats": ["%s"]
                }
                """.formatted(SEAT);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                BASE_URL +
                                "/shows/" +
                                SHOW_ID +
                                "/reserve"
                        ))
                        .header(
                                "Authorization",
                                "Bearer idempotency-user"
                        )
                        .header(
                                "Idempotency-Key",
                                "same-booking-key"
                        )
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(json)
                        )
                        .build();

        return client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static String extractReservationId(
            String body) {

        String key = "\"reservationId\":\"";

        int start = body.indexOf(key);

        if (start == -1) {
            return null;
        }

        start += key.length();

        int end = body.indexOf("\"", start);

        return body.substring(start, end);
    }
}