import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class BurstTest {

   private static final String BASE_URL = "http://localhost:8081";
private static final int SHOW_ID = 3;
private static final String SEAT = "B1";
private static final int TOTAL_REQUESTS = 100;

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newHttpClient();

        ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();

        List<Future<Integer>> futures = new ArrayList<>();

        long start = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {

            int requestNumber = i;

            futures.add(
                executor.submit(() ->
                    reserve(client, requestNumber)
                )
            );
        }

        int confirmed = 0;
        int conflict = 0;
        int serverErrors = 0;
        int otherErrors = 0;

        for (Future<Integer> future : futures) {

            int status = future.get();

           if (status == 200 || status == 201) {
    confirmed++;

                            } else if (status == 409) {
                conflict++;
            } else if (status >= 500) {
                serverErrors++;
            } else {
                otherErrors++;
            }
        }

        executor.close();

        long duration = System.currentTimeMillis() - start;

        System.out.println();
        System.out.println("========== BURST TEST ==========");
        System.out.println("Total requests : " + TOTAL_REQUESTS);
        System.out.println("Confirmed      : " + confirmed);
        System.out.println("409 Conflict   : " + conflict);
        System.out.println("5xx Errors     : " + serverErrors);
        System.out.println("Other Errors   : " + otherErrors);
        System.out.println("Duration       : " + duration + " ms");
        System.out.println("================================");
    }

    private static int reserve(
            HttpClient client,
            int requestNumber) throws Exception {

        String userId = "burst-user-" + requestNumber;
        String idempotencyKey = "burst-" + requestNumber;

        String json = """
                {
                    "seats": ["%s"]
                }
                """.formatted(SEAT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        BASE_URL + "/shows/" + SHOW_ID + "/reserve"
                ))
                .header(
                        "Authorization",
                        "Bearer " + userId
                )
                .header(
                        "Idempotency-Key",
                        idempotencyKey
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(json)
                )
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        return response.statusCode();
    }
}