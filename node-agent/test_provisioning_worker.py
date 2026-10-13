import json
import pathlib
import sys
import unittest
from unittest.mock import MagicMock, patch

# The directory name contains a hyphen, so unittest discovery may not add it
# to sys.path when importing tests as top-level modules.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import provisioning_worker as worker


VALID_JOB = {
    "id": "job_test_123",
    "action": "add",
    "inbound_tag": "vless-in",
    "client_id": "d9428888-122b-4f4e-8a06-2a3a5f10b001",
    "client_email": "user-123@spdcon.local",
}


class ProvisioningWorkerTests(unittest.TestCase):
    def test_rejects_incomplete_job_without_touching_xray(self):
        with patch.object(worker, "change_client") as change:
            result = worker.process_job({"id": "job_missing"}, "/tmp/config.json", "xray", False)
        self.assertEqual(result["status"], "failed")
        change.assert_not_called()

    def test_dry_run_reports_validated_not_applied(self):
        with patch.object(worker, "change_client", return_value={
            "ok": True, "changed": True, "applied": False,
            "message": "Candidate validates",
        }) as change:
            result = worker.process_job(VALID_JOB, "/etc/xray/config.json", "/usr/bin/xray", False)
        self.assertEqual(result["status"], "validated")
        change.assert_called_once_with(
            config_path="/etc/xray/config.json",
            inbound_tag="vless-in",
            client_id=VALID_JOB["client_id"],
            email=VALID_JOB["client_email"],
            action="add",
            xray_bin="/usr/bin/xray",
            apply=False,
        )

    def test_applied_file_change_warns_that_restart_is_still_required(self):
        with patch.object(worker, "change_client", return_value={
            "ok": True, "changed": True, "applied": True,
            "message": "Applied",
        }):
            result = worker.process_job(VALID_JOB, "/etc/xray/config.json", "xray", True)
        self.assertEqual(result["status"], "config_pending_restart")
        self.assertIn("reload", result["message"])

    def test_invalid_candidate_is_reported_as_failure(self):
        with patch.object(worker, "change_client", return_value={
            "ok": False, "changed": True, "applied": False,
            "message": "Xray rejected candidate",
        }):
            result = worker.process_job(VALID_JOB, "/etc/xray/config.json", "xray", True)
        self.assertEqual(result["status"], "failed")
        self.assertIn("rejected", result["message"])

    def test_local_provisioning_errors_are_reported_without_traceback(self):
        with patch.object(worker, "change_client", side_effect=ValueError("bad inbound")):
            result = worker.process_job(VALID_JOB, "/etc/xray/config.json", "xray", False)
        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["message"], "bad inbound")

    def test_external_api_requires_https(self):
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            worker.api_request("http://api.example.com", "node-1", "secret", "/health")

    def test_api_request_sends_bearer_token_and_json(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{"job": null}'
        with patch.object(worker.urllib.request, "urlopen", return_value=response) as open_url:
            result = worker.api_request(
                "https://api.example.com/", "node-1", "secret", "/jobs",
                method="POST", body={"status": "validated"},
            )
        self.assertEqual(result, {"job": None})
        request = open_url.call_args.args[0]
        self.assertEqual(request.get_header("Authorization"), "Bearer secret")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(json.loads(request.data), {"status": "validated"})

    def test_worker_claims_and_reports_one_job(self):
        claimed = {"job": VALID_JOB}
        calls = []

        def fake_request(api_base, node_id, node_secret, path, method="GET", body=None, timeout=10.0):
            calls.append((path, method, body))
            if method == "GET":
                return claimed
            return {"status": body["status"]}

        with patch.object(worker, "api_request", side_effect=fake_request), \
             patch.object(worker, "process_job", return_value={
                 "status": "validated", "message": "Candidate validates",
             }) as process:
            code = worker.run_worker(
                "https://api.example.com", "node-1", "secret",
                "/etc/xray/config.json", "xray", False, 1, once=True,
            )
        self.assertEqual(code, 0)
        process.assert_called_once_with(VALID_JOB, "/etc/xray/config.json", "xray", False)
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[1][1], "POST")
        self.assertEqual(calls[1][2]["status"], "validated")
        self.assertIn("/result", calls[1][0])


if __name__ == "__main__":
    unittest.main()
