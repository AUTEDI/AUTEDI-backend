"""One explicitly authorized local service generation; never retries paid work.

Credentials stay in memory/private ignored files. HTTP errors never dump bodies.
Run from autedi: python scripts/local_service_smoke.py --authorize-one-generation
"""
import argparse
import hashlib
import http.client
import json
import re
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/manual-service-test"
RECEIPT = OUT / "receipt.json"


def now():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def local_env():
    values = {}
    for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
        if line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    target = urlsplit(values.get("LOCAL_DB_URL", "").removeprefix("jdbc:"))
    check(target.hostname in ("localhost", "127.0.0.1", "::1"), "Refusing a non-local database configuration")
    check(target.path.lstrip("/") == "autedi_ai_local", "Expected the dedicated autedi_ai_local database")
    return values


def login_settings(values):
    private = ROOT / "http/http-client.private.env.json"
    if private.exists():
        settings = json.loads(private.read_text(encoding="utf-8"))["local"]
        return settings["loginEmail"], settings["loginPassword"]
    if values.get("TEST_LOGIN_EMAIL") and values.get("TEST_LOGIN_PASSWORD"):
        return values["TEST_LOGIN_EMAIL"], values["TEST_LOGIN_PASSWORD"]
    # Existing local profile seed account; never print or copy it to shared files.
    source = (ROOT / "src/main/java/com/ceos24/autedi/global/config/DevDataInitializer.java").read_text(encoding="utf-8")
    source = source[source.index("User teacher"):]
    email = re.search(r'\.email\("([^"\n]+)"\)', source)
    password = re.search(r'passwordEncoder\.encode\("([^"\n]+)"\)', source)
    check(email is not None and password is not None, "Configure local test login in the private environment file")
    return email.group(1), password.group(1)


class Api:
    def __init__(self):
        self.token = None

    def call(self, method, path, expected, body=None, headers=None, raw=False):
        check(path.startswith("/api/v1/"), "Only local application API paths are allowed")
        request_headers = dict(headers or {})
        if self.token:
            request_headers["Authorization"] = "Bearer " + self.token
        if isinstance(body, dict):
            body = json.dumps(body).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        connection = http.client.HTTPConnection("127.0.0.1", 8080, timeout=180)
        try:
            connection.request(method, path, body=body, headers=request_headers)
            response = connection.getresponse()
            data = response.read()
            response_headers = {k.lower(): v for k, v in response.getheaders()}
            check(response.status == expected, f"{method} {path} returned HTTP {response.status}, expected {expected}")
            if raw or not data:
                return data, response_headers
            result = json.loads(data)
            check(result.get("isSuccess") is True, f"{method} {path} did not succeed")
            return result["result"], response_headers
        except TimeoutError:
            raise RuntimeError(f"Local API timed out: {method} {path}; check the stored outcome before repeating") from None
        finally:
            connection.close()


def preserved(before, after):
    for name in ("aiInitialContent", "originalText", "explanation", "action", "feedbackType", "issueKinds",
                 "anchor", "regions", "warnings", "locationUncertain", "requiresReview"):
        check(before.get(name) == after.get(name), "Original AI field changed: " + name)


def run(args):
    global OUT, RECEIPT
    OUT = (ROOT / args.output_dir).resolve()
    check(OUT.is_relative_to(ROOT / "build"), "Output must stay under the project build directory")
    RECEIPT = OUT / "receipt.json"
    image_path = (ROOT / args.image).resolve()
    check(image_path.is_relative_to(ROOT.parent / "test_data") and image_path.suffix.lower() in (".jpg", ".jpeg", ".png"),
          "Use a JPG/JPEG/PNG in the local test_data directory")
    check(not args.resume or RECEIPT.exists(), "No existing receipt to resume")
    OUT.mkdir(parents=True, exist_ok=True)
    receipt = json.loads(RECEIPT.read_text(encoding="utf-8")) if RECEIPT.exists() else {"startedAt": now(), "steps": []}
    if receipt.get("finishedAt"):
        print("A completed receipt already exists; no new generation was submitted.", flush=True)
        return
    check(not receipt.get("generationSubmissionAttempted") or receipt.get("generationId"),
          "Previous submission outcome is unknown; refusing to submit again")
    values = local_env()
    email, password = login_settings(values)
    api = Api()
    login, _ = api.call("POST", "/api/v1/auth/login", 200, {"email": email, "password": password})
    api.token = login["accessToken"]
    private = ROOT / "http/http-client.private.env.json"
    if not private.exists():
        write_json(private, {"local": {"loginEmail": email, "loginPassword": password}})
    print("Local login succeeded; credentials hidden.", flush=True)

    if not receipt.get("generationSubmissionAttempted"):
        check(args.authorize_one_generation, "Explicit --authorize-one-generation is required")
        image = image_path.read_bytes()
        boundary = "autedi-smoke-" + uuid.uuid4().hex
        mime = "image/png" if image_path.suffix.lower() == ".png" else "image/jpeg"
        multipart = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{image_path.name}\"\r\n"
                     f"Content-Type: {mime}\r\n\r\n").encode() + image + f"\r\n--{boundary}--\r\n".encode()
        if receipt.get("worksheetId"):
            check(receipt.get("sourceHash") == hashlib.sha256(image).hexdigest(), "Receipt belongs to another image")
            worksheet, _ = api.call("GET", f"/api/v1/worksheets/{receipt['worksheetId']}", 200)
            check(worksheet["status"] == "DRAFT", "Prepared worksheet is no longer DRAFT")
        else:
            worksheet, _ = api.call("POST", "/api/v1/worksheets", 201, multipart,
                                    {"Content-Type": "multipart/form-data; boundary=" + boundary})
        wid = worksheet["id"]
        base = f"/api/v1/worksheets/{wid}"
        receipt.update({"worksheetId": wid, "sourceImage": image_path.relative_to(ROOT.parent).as_posix(),
                        "sourceHash": hashlib.sha256(image).hexdigest(), "steps": ["login", "upload"]})
        write_json(RECEIPT, receipt)
        original, _ = api.call("GET", base + "/file", 200, raw=True)
        check(original == image, "Original image bytes changed")
        png, _ = api.call("GET", base + "/pages/1/image", 200, raw=True)
        check(png.startswith(b"\x89PNG\r\n\x1a\n"), "Prepared image is not PNG")
        check(len(worksheet["pages"]) == 1, "Expected one worksheet page")
        page = worksheet["pages"][0]
        check(int.from_bytes(png[16:20], "big") == page["width"]
              and int.from_bytes(png[20:24], "big") == page["height"], "Prepared PNG dimensions differ from page")
        check(page["regionCoordinateUnit"] == "NORMALIZED_0_1", "Unexpected page coordinate unit")
        local_prepared = ROOT / "build/hdr-image-test" / (image_path.stem + "-prepared.png")
        if local_prepared.exists():
            check(png == local_prepared.read_bytes(), "Server PNG differs from the locally verified HDR primary image")
            receipt["hdrPrimaryPngVerified"] = True
        (OUT / "prepared.png").write_bytes(png)
        receipt["preparedHash"] = hashlib.sha256(png).hexdigest()
        receipt["page"] = worksheet["pages"][0]
        api.call("PUT", base + "/settings", 200, {"areas": [
            {"area": "GRAMMAR_SPELLING", "level": "STANDARD"},
            {"area": "CONTENT_SUMMARY", "level": "ADVANCED"}]})
        receipt["steps"].append("settings")
        receipt.update({"idempotencyKey": "manual-smoke-" + uuid.uuid4().hex,
                        "generationSubmissionAttempted": True})
        write_json(RECEIPT, receipt)
        print(f"Original bytes and prepared PNG verified: {image_path.name}, {page['width']}x{page['height']}.", flush=True)
        generation, _ = api.call("POST", base + "/generations", 202,
                                 headers={"Idempotency-Key": receipt["idempotencyKey"]})
        receipt["generationId"] = generation["id"]
        receipt["steps"].append("generation-accepted")
        write_json(RECEIPT, receipt)
        duplicate, _ = api.call("POST", base + "/generations", 202,
                                headers={"Idempotency-Key": receipt["idempotencyKey"]})
        check(duplicate["id"] == generation["id"], "Same idempotency key created a different job")
        receipt["idempotencyVerified"] = True
        write_json(RECEIPT, receipt)
        print(f"Generation accepted: worksheet={wid}, generation={generation['id']}; one paid workflow only.", flush=True)

    base = f"/api/v1/worksheets/{receipt['worksheetId']}"
    deadline = time.monotonic() + 420
    previous = None
    while True:
        generation, _ = api.call("GET", base + f"/generations/{receipt['generationId']}", 200)
        state = generation["status"], generation["stage"]
        if state != previous:
            print("Generation state: " + "/".join(state), flush=True)
            receipt.setdefault("states", []).append({"at": now(), "status": state[0], "stage": state[1]})
            previous = state
        receipt["generation"] = generation
        write_json(RECEIPT, receipt)
        if generation["status"] == "FAILED":
            receipt["finishedAt"] = now()
            receipt["outcome"] = "GENERATION_FAILED_NO_RETRY"
            write_json(RECEIPT, receipt)
            print("Generation failed; no paid retry submitted. code=" + str((generation.get("error") or {}).get("code")), flush=True)
            return
        if generation["status"] == "SUCCEEDED":
            break
        check(time.monotonic() < deadline, "Polling deadline reached; use --resume to query this job, never resubmit")
        time.sleep(2)

    review, _ = api.call("GET", base + "/review", 200)
    write_json(OUT / "review-before.json", review)
    check(review["worksheet"]["status"] == "READY", "Worksheet is not READY")
    results = review.get("aiResults", [])
    check(results and results[0]["metadata"]["model"] == "gpt-6.1-terra", "Result was not generated in TERRA mode")
    receipt["resultMetadata"] = results[0]["metadata"]
    receipt["counts"] = {"cards": len(review["corrections"]), "reviewIssues": len(review.get("reviewIssues", [])),
                         "omitted": sum(a["omittedCount"] for a in review.get("summary", []))}
    receipt["steps"].extend(["status-succeeded", "stored-review-loaded"])
    cards = review["corrections"]
    page = review["worksheet"]["pages"][0]
    for card in cards:
        check(card["regionCoordinateUnit"] == "NORMALIZED_0_1", "Unexpected coordinate unit")
        check(card["anchor"]["transcriptionId"] == results[0]["transcriptionId"], "Anchor transcription mismatch")
        for box in card["regions"]:
            check(box["pageId"] == page["id"], "Coordinate page mismatch")
            check(0 <= box["x"] <= 1 and 0 <= box["y"] <= 1 and box["width"] > 0 and box["height"] > 0,
                  "Invalid normalized region")
            check(box["x"] + box["width"] <= 1.000000001 and box["y"] + box["height"] <= 1.000000001,
                  "Region outside prepared image")
    if cards and not receipt.get("teacherLifecycleVerified"):
        by_action = {}
        for card in cards:
            by_action.setdefault(card["action"], card)
        for initial in by_action.values():
            if initial["id"] in receipt.get("teacherVerifiedCorrectionIds", []):
                continue
            url = base + f"/corrections/{initial['id']}"
            edited_text = initial["content"] + " [manual service check]"
            edited, _ = api.call("PATCH", url, 200, {"content": edited_text}, {"If-Match": initial["etag"]})
            preserved(initial, edited)
            check(edited["content"] == edited_text and edited["editedByTeacher"], "Teacher edit was not stored")
            _, deleted_headers = api.call("DELETE", url, 204, headers={"If-Match": edited["etag"]})
            restored, _ = api.call("POST", url + "/restore", 200, headers={"If-Match": deleted_headers["etag"]})
            preserved(initial, restored)
            check(restored["content"] == edited_text and restored["status"] == "ACTIVE", "Restore lost edited content")
            stored, _ = api.call("GET", url, 200)
            preserved(initial, stored)
            check(stored["content"] == edited_text, "Teacher edit did not survive a fresh read")
            receipt.setdefault("teacherVerifiedCorrectionIds", []).append(initial["id"])
            receipt.setdefault("teacherVerifiedActions", []).append(initial["action"])
            write_json(RECEIPT, receipt)
        receipt["teacherLifecycleVerified"] = True
        receipt["editedCorrectionId"] = receipt["teacherVerifiedCorrectionIds"][0]
        receipt["steps"].extend(["teacher-edit", "soft-delete", "restore"])
    after, _ = api.call("GET", base + "/review", 200)
    write_json(OUT / "review-after.json", after)
    check(review.get("reviewIssues") == after.get("reviewIssues"), "Review issues changed after teacher edit")
    check(review.get("aiResults") == after.get("aiResults"), "AI result references changed")
    original_after, _ = api.call("GET", base + "/file", 200, raw=True)
    png_after, _ = api.call("GET", base + "/pages/1/image", 200, raw=True)
    check(hashlib.sha256(original_after).hexdigest() == receipt["sourceHash"], "Original changed after teacher edits")
    check(hashlib.sha256(png_after).hexdigest() == receipt["preparedHash"], "Prepared PNG changed after teacher edits")
    receipt.update({"outcome": "PASSED" if cards else "SUCCEEDED_WITHOUT_CARDS", "finishedAt": now(),
                    "coordinatesVerified": True, "paidWorkflowSubmissions": 1})
    write_json(RECEIPT, receipt)
    print("Service test finished: " + receipt["outcome"] + "; counts=" + json.dumps(receipt["counts"]), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--authorize-one-generation", action="store_true")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--image", default="../test_data/testdata.jpg")
    parser.add_argument("--output-dir", default="build/manual-service-test")
    options = parser.parse_args()
    try:
        run(options)
    except Exception as error:
        # Avoid tracebacks/response bodies that might include credentials.
        print("Stopped: " + (str(error) if isinstance(error, RuntimeError) else type(error).__name__), flush=True)
        sys.exit(1)
