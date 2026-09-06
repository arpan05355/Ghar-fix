import urllib.request
import urllib.parse
import json
import time

BASE_URL = "http://localhost:5000"

def run_tests():
    ctx = {"auth_token": None}

    def make_req(endpoint, method="GET", json_body=None):
        url = f"{BASE_URL}{endpoint}"
        headers = {}
        if ctx["auth_token"]:
            headers["Authorization"] = f"Bearer {ctx['auth_token']}"
        data = None
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as resp:
                status = resp.status
                body = resp.read().decode("utf-8")
                try:
                    return status, json.loads(body)
                except Exception:
                    return status, body
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8")
            try:
                parsed = json.loads(err_body)
            except Exception:
                parsed = err_body
            return e.code, parsed

    print("=== 1. Test unauthenticated /api/ai/bookings/active ===")
    status, body = make_req("/api/ai/bookings/active")
    print(f"Status: {status}, Body: {body}")
    assert status == 401
    assert body.get("error") == "Unauthorized"

    print("\n=== 2. Test unauthenticated confirmation attempt ===")
    status, body = make_req("/api/ai/actions/confirm", method="POST", json_body={"confirmationToken": "tok_invalid_12345"})
    print(f"Status: {status}, Body: {body}")
    assert status == 401
    assert body.get("error") == "Unauthorized"

    print("\n=== 3. Register and login test user via /api/auth ===")
    email = f"phase3_user_{int(time.time())}@gharfix.com"
    pwd = "Password123!"
    reg_data = {
        "name": "Phase3 Tester",
        "email": email,
        "phone": "9876543210",
        "password": pwd,
        "confirmPassword": pwd,
        "address": "123 Phase 3 Lane"
    }
    status, body = make_req("/api/auth/register", method="POST", json_body=reg_data)
    print(f"Register status: {status}, Body: {body}")
    assert status == 200 and body.get("success") is True

    login_data = {
        "email": email,
        "password": pwd
    }
    status, body = make_req("/api/auth/login", method="POST", json_body=login_data)
    print(f"Login status: {status}, Token received: {bool(body.get('token'))}")
    assert status == 200
    ctx["auth_token"] = body.get("token")
    assert ctx["auth_token"] is not None

    print("\n=== 4. Test authenticated with invalid confirmation token ===")
    status, body = make_req("/api/ai/actions/confirm", method="POST", json_body={"confirmationToken": "tok_invalid_12345"})
    print(f"Status: {status}, Body: {body}")
    assert status == 400
    assert "Invalid or expired" in body.get("message", "")

    print("\n=== 5. Test authenticated /api/ai/bookings/active ===")
    status, body = make_req("/api/ai/bookings/active")
    print(f"Status: {status}, Body: {body}")
    assert status == 200
    assert body.get("success") is True
    print(f"Active bookings count: {body.get('count', 0)}")

    print("\n=== 6. Test AI Chat with Complaint / Issue intent ===")
    chat_payload = {
        "message": "I was unhappy with my electrician service yesterday and want to file a formal complaint."
    }
    status, body = make_req("/api/ai/chat", method="POST", json_body=chat_payload)
    print(f"Chat status: {status}")
    print("Chat response:")
    print(json.dumps(body, indent=2))
    
    action_prop = body.get("proposedAction")
    assert action_prop is not None
    assert action_prop.get("actionType") == "REPORT_ISSUE"
    tok = action_prop.get("confirmationToken")
    print(f"\nObtained Confirmation Token: {tok}")
    assert tok is not None

    print("\n=== 7. Confirming proposed action with confirmationToken ===")
    status, body = make_req("/api/ai/actions/confirm", method="POST", json_body={"confirmationToken": tok, "userNotes": "Customer confirmed through AI Assistant"})
    print(f"Confirm status: {status}, Body: {body}")
    assert status == 200
    assert body.get("success") is True
    ticket = body.get("referenceId")
    print(f"Registered Complaint Ticket: {ticket}")
    assert ticket is not None and ticket.startswith("GF-CMP-")

    print("\n=== 8. Test replay attack (using same single-use token again) ===")
    status, body = make_req("/api/ai/actions/confirm", method="POST", json_body={"confirmationToken": tok})
    print(f"Replay status: {status}, Body: {body}")
    assert status == 400
    assert "Invalid or expired" in body.get("message", "")

    print("\n=== 9. Query complaints for user ===")
    status, body = make_req("/api/ai/complaints")
    assert status == 200
    complaint_list = body.get("complaints", [])
    assert len(complaint_list) >= 1
    assert any(c.get("ticketNumber") == ticket for c in complaint_list)

    print("\n=== 10. Test AI Chat with Booking Assistance ===")
    book_chat = {
        "message": "I would like to book a Plumber for tomorrow morning."
    }
    status, body = make_req("/api/ai/chat", method="POST", json_body=book_chat)
    print(f"Booking Chat status: {status}")
    book_prop = body.get("proposedAction")
    print(f"Booking Proposal: {book_prop}")
    assert book_prop is not None
    assert book_prop.get("actionType") == "BOOK_SERVICE"
    assert book_prop.get("confirmationToken") is not None

    print("\n=== 11. Confirm Booking via confirmationToken ===")
    status, body = make_req("/api/ai/actions/confirm", method="POST", json_body={"confirmationToken": book_prop.get("confirmationToken")})
    print(f"Booking Confirm status: {status}, Body: {body}")
    assert status == 200
    assert body.get("success") is True
    booking_id = body.get("referenceId")
    print(f"Created Booking ID: {booking_id}")
    assert booking_id is not None

    print("\n=== 12. Test authenticated /api/ai/bookings/active after booking ===")
    status, body = make_req("/api/ai/bookings/active")
    print(f"Active bookings after booking: {len(body.get('bookings', []))}")
    assert len(body.get("bookings", [])) >= 1

    print("\n=== 13. Test AI Chat Cancellation with newly created booking ===")
    cancel_chat = {
        "message": f"Please cancel my booking #{booking_id} because my schedule changed."
    }
    status, body = make_req("/api/ai/chat", method="POST", json_body=cancel_chat)
    print(f"Cancel Chat status: {status}")
    cancel_prop = body.get("proposedAction")
    print(f"Cancel Proposal: {cancel_prop}")
    assert cancel_prop is not None
    assert cancel_prop.get("actionType") == "CANCEL_BOOKING"
    assert cancel_prop.get("confirmationToken") is not None

    print("\n=== 14. Confirm Cancellation via confirmationToken ===")
    status, body = make_req("/api/ai/actions/confirm", method="POST", json_body={"confirmationToken": cancel_prop.get("confirmationToken")})
    print(f"Cancel Confirm status: {status}, Body: {body}")
    assert status == 200
    assert body.get("success") is True
    print(f"Cancellation confirmed for booking: {body.get('referenceId')}")

    print("\n============================================================")
    print(">>> ALL 14 PHASE 3 LIVE E2E VERIFICATIONS PASSED 100%! <<<")
    print("============================================================")

if __name__ == "__main__":
    run_tests()
