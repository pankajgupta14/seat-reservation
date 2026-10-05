import concurrent.futures
import requests

BASE_URL = "http://localhost:8081"
SHOW_ID = 3
SEAT = "B1"
TOTAL_REQUESTS = 100


def reserve(i):
    user_id = f"burst-user-{i}"
    idempotency_key = f"burst-{i}"

    headers = {
        "Authorization": f"Bearer {user_id}",
        "Idempotency-Key": idempotency_key,
        "Content-Type": "application/json"
    }

    body = {
        "seats": [SEAT]
    }

    try:
        response = requests.post(
            f"{BASE_URL}/shows/{SHOW_ID}/reserve",
            headers=headers,
            json=body,
            timeout=30
        )

        return response.status_code, response.text

    except Exception as e:
        return "ERROR", str(e)


with concurrent.futures.ThreadPoolExecutor(
        max_workers=TOTAL_REQUESTS) as executor:

    results = list(
        executor.map(
            reserve,
            range(TOTAL_REQUESTS)
        )
    )


confirmed = 0
conflict = 0
server_errors = 0
errors = 0

for status, body in results:

    if status in (200, 201):
        confirmed += 1

    elif status == 409:
        conflict += 1

    elif isinstance(status, int) and status >= 500:
        server_errors += 1

    else:
        errors += 1


print()
print("========== BURST TEST ==========")
print(f"Total requests : {TOTAL_REQUESTS}")
print(f"Confirmed      : {confirmed}")
print(f"409 Conflict   : {conflict}")
print(f"5xx Errors     : {server_errors}")
print(f"Other Errors   : {errors}")
print("================================")