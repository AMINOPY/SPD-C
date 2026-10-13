import os
import tempfile
import unittest

from fastapi.testclient import TestClient

from app import main as api


class ConfigHubApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        api.DATABASE_PATH = os.path.join(self.temp.name, "test.db")
        api.APP_SECRET = "test-secret-that-is-long-enough-for-bootstrap"
        api.BOOTSTRAP_ADMIN_TOKEN = "test-one-time-bootstrap-token"
        api.init_db()
        self.client_context = TestClient(api.app)
        self.client = self.client_context.__enter__()

    def tearDown(self):
        self.client_context.__exit__(None, None, None)
        self.temp.cleanup()

    def register(self, username="test_user"):
        response = self.client.post("/auth/register", json={
            "username": username, "password": "a-very-long-test-password"
        })
        self.assertEqual(response.status_code, 201, response.text)
        return response.json()["user"]

    def login(self, username="test_user"):
        response = self.client.post("/auth/login", json={
            "username": username, "password": "a-very-long-test-password"
        })
        self.assertEqual(response.status_code, 200, response.text)
        return {"Authorization": "Bearer " + response.json()["access_token"]}

    def test_web_panel_is_served(self):
        response = self.client.get("/panel/")
        self.assertEqual(response.status_code, 200)
        self.assertIn("Spd con", response.text)

    def test_password_hash_is_salted_and_verified(self):
        first = api.password_hash("same-password")
        second = api.password_hash("same-password")
        self.assertNotEqual(first, second)
        self.assertTrue(api.verify_password("same-password", first))
        self.assertFalse(api.verify_password("wrong-password", first))

    def test_config_is_private_to_owner(self):
        self.register()
        token = self.login()
        created = self.client.post("/configs", headers=token, json={
            "name": "Main", "protocol": "vless",
            "profile": {"server": "example.org", "port": 443}
        })
        self.assertEqual(created.status_code, 201, created.text)
        self.assertEqual(len(self.client.get("/configs", headers=token).json()), 1)
        other = self.client.post("/auth/register", json={
            "username": "other_user", "password": "a-very-long-test-password"
        })
        self.assertEqual(other.status_code, 201)
        self.assertEqual(self.client.get("/configs", headers=self.login("other_user")).json(), [])

    def test_subscription_can_be_revoked_and_quota_applied(self):
        user = self.register()
        token = self.login()
        self.client.post("/configs", headers=token, json={
            "name": "Main", "protocol": "vless", "profile": {"server": "example.org"}
        })
        created = self.client.post("/subscriptions", headers=token, json={})
        self.assertEqual(created.status_code, 201, created.text)
        url = created.json()["url"]
        fetched = self.client.get(url)
        self.assertEqual(fetched.status_code, 200, fetched.text)
        sub_id = created.json()["id"]
        revoked = self.client.delete(f"/subscriptions/{sub_id}", headers=token)
        self.assertEqual(revoked.status_code, 200, revoked.text)
        self.assertEqual(self.client.get(url).status_code, 404)


    def test_subscription_is_blocked_when_reported_quota_is_exhausted(self):
        user = self.register()
        token = self.login()
        self.client.post("/configs", headers=token, json={
            "name": "Quota profile", "protocol": "vless", "profile": {"server": "example.org"}
        })
        subscription = self.client.post("/subscriptions", headers=token, json={}).json()
        bootstrap = self.client.post("/admin/bootstrap", headers={
            "X-Bootstrap-Token": api.BOOTSTRAP_ADMIN_TOKEN
        })
        self.assertEqual(bootstrap.status_code, 201, bootstrap.text)
        admin_token = self.login()
        limits = self.client.patch(f"/admin/users/{user['id']}/limits", headers=admin_token,
            json={"quota_bytes": 100, "expires_at": None, "enabled": True})
        self.assertEqual(limits.status_code, 200, limits.text)
        usage = self.client.post(f"/admin/users/{user['id']}/usage", headers=admin_token,
            json={"node_id": "test-node", "delta_bytes": 100})
        self.assertEqual(usage.status_code, 201, usage.text)
        self.assertEqual(self.client.get(subscription["url"]).status_code, 404)

    def test_connection_policy_and_ip_observations_are_admin_scoped(self):
        admin_user = self.register("admin_user")
        bootstrap = self.client.post("/admin/bootstrap", headers={
            "X-Bootstrap-Token": api.BOOTSTRAP_ADMIN_TOKEN
        })
        self.assertEqual(bootstrap.status_code, 201, bootstrap.text)
        admin_headers = self.login("admin_user")

        user_response = self.client.post("/auth/register", json={
            "username": "vpn_user", "password": "a-very-long-test-password"
        })
        self.assertEqual(user_response.status_code, 201, user_response.text)
        user_id = user_response.json()["user"]["id"]

        policy = self.client.put(f"/admin/users/{user_id}/connection-policy",
            headers=admin_headers, json={
                "max_distinct_ips": 1, "max_concurrent_connections": 2,
                "action": "deny_new_ip"
            })
        self.assertEqual(policy.status_code, 200, policy.text)
        self.assertEqual(policy.json()["enforcement"], "pending_node_agent")

        first = self.client.post(f"/admin/users/{user_id}/connections/report",
            headers=admin_headers, json={
                "connection_id": "connection-first", "node_id": "node-a",
                "client_ip": "192.0.2.10", "state": "active",
                "upload_bytes": 100, "download_bytes": 200
            })
        self.assertEqual(first.status_code, 201, first.text)
        self.assertEqual(first.json()["alerts"], [])

        second = self.client.post(f"/admin/users/{user_id}/connections/report",
            headers=admin_headers, json={
                "connection_id": "connection-second", "node_id": "node-a",
                "client_ip": "2001:db8::10", "state": "active"
            })
        self.assertEqual(second.status_code, 201, second.text)
        self.assertIn("distinct_ip_limit_exceeded", second.json()["alerts"])
        self.assertEqual(second.json()["requested_policy_action"], "deny_new_ip")
        self.assertEqual(second.json()["enforcement"], "not_enforced_node_agent_required")

        connections = self.client.get(
            f"/admin/users/{user_id}/connections", headers=admin_headers)
        self.assertEqual(connections.status_code, 200, connections.text)
        self.assertEqual(len(connections.json()["connections"]), 2)

        invalid_ip = self.client.post(f"/admin/users/{user_id}/connections/report",
            headers=admin_headers, json={
                "connection_id": "connection-invalid", "node_id": "node-a",
                "client_ip": "not-an-ip", "state": "active"
            })
        self.assertEqual(invalid_ip.status_code, 422)

        non_admin = self.client.get(
            f"/admin/users/{user_id}/connections", headers=self.login("vpn_user"))
        self.assertEqual(non_admin.status_code, 403)

    def test_enrolled_node_can_report_once_and_disabled_node_is_rejected(self):
        self.register("node_admin")
        self.assertEqual(self.client.post("/admin/bootstrap", headers={
            "X-Bootstrap-Token": api.BOOTSTRAP_ADMIN_TOKEN
        }).status_code, 201)
        admin_headers = self.login("node_admin")
        user = self.client.post("/auth/register", json={
            "username": "node_user", "password": "a-very-long-test-password"
        }).json()["user"]
        enrolled = self.client.post("/admin/nodes", headers=admin_headers,
            json={"name": "edge-test"})
        self.assertEqual(enrolled.status_code, 201, enrolled.text)
        node = enrolled.json()
        self.assertIn("node_secret", node)
        self.assertEqual(self.client.get("/admin/nodes", headers=admin_headers).status_code, 200)

        event = {
            "event_id": "event-unique-123456789", "event_timestamp": api.now(),
            "user_id": user["id"], "connection_id": "node-connection-123",
            "node_id": node["id"], "client_ip": "2001:db8::8",
            "state": "active", "upload_bytes": 100, "download_bytes": 300
        }
        headers = {"Authorization": "Bearer " + node["node_secret"]}
        first = self.client.post(f"/nodes/{node['id']}/connections/report",
                                 headers=headers, json=event)
        self.assertEqual(first.status_code, 201, first.text)
        self.assertTrue(first.json()["accepted"])
        self.assertEqual(first.json()["client_ip"], "2001:db8::8")
        self.assertEqual(first.json()["usage_delta_bytes"], 400)
        with api.db() as conn:
            self.assertEqual(conn.execute("SELECT used_bytes FROM users WHERE id=?",
                                          (user["id"],)).fetchone()["used_bytes"], 400)

        # A later cumulative snapshot charges only the increase, not the whole counter.
        updated_event = dict(event, event_id="event-unique-987654321",
                             event_timestamp=api.now(), upload_bytes=150, download_bytes=325)
        updated = self.client.post(f"/nodes/{node['id']}/connections/report",
                                   headers=headers, json=updated_event)
        self.assertEqual(updated.status_code, 201, updated.text)
        self.assertEqual(updated.json()["usage_delta_bytes"], 75)
        with api.db() as conn:
            self.assertEqual(conn.execute("SELECT used_bytes FROM users WHERE id=?",
                                          (user["id"],)).fetchone()["used_bytes"], 475)

        replay = self.client.post(f"/nodes/{node['id']}/connections/report",
                                  headers=headers, json=event)
        self.assertEqual(replay.status_code, 409)

        stale = dict(event, event_id="event-stale-123456789", event_timestamp=api.now() - 600)
        self.assertEqual(self.client.post(f"/nodes/{node['id']}/connections/report",
            headers=headers, json=stale).status_code, 401)

        disabled = self.client.patch(f"/admin/nodes/{node['id']}/enabled",
            headers=admin_headers, params={"enabled": "false"})
        self.assertEqual(disabled.status_code, 200, disabled.text)
        blocked = dict(event, event_id="event-disabled-123456")
        self.assertEqual(self.client.post(f"/nodes/{node['id']}/connections/report",
            headers=headers, json=blocked).status_code, 401)

    def test_xray_usage_snapshots_are_idempotent_and_handle_counter_reset(self):
        self.register("stats_admin")
        self.assertEqual(self.client.post("/admin/bootstrap", headers={
            "X-Bootstrap-Token": api.BOOTSTRAP_ADMIN_TOKEN
        }).status_code, 201)
        admin_headers = self.login("stats_admin")
        user = self.client.post("/auth/register", json={
            "username": "stats_user", "password": "a-very-long-test-password"
        }).json()["user"]
        limits = self.client.patch(f"/admin/users/{user['id']}/limits", headers=admin_headers,
            json={"quota_bytes": 500, "expires_at": None, "enabled": True})
        self.assertEqual(limits.status_code, 200, limits.text)
        node = self.client.post("/admin/nodes", headers=admin_headers,
            json={"name": "stats-node"}).json()
        headers = {"Authorization": "Bearer " + node["node_secret"]}
        base = {"event_timestamp": api.now(), "user_id": user["id"],
                "upload_bytes": 100, "download_bytes": 200}
        first = self.client.post(f"/nodes/{node['id']}/usage/report", headers=headers,
            json=dict(base, event_id="stats-event-first-123"))
        self.assertEqual(first.status_code, 201, first.text)
        self.assertEqual(first.json()["usage_delta_bytes"], 300)
        self.assertFalse(first.json()["quota_exhausted"])

        second = self.client.post(f"/nodes/{node['id']}/usage/report", headers=headers,
            json=dict(base, event_id="stats-event-second-123", event_timestamp=api.now(),
                      upload_bytes=150, download_bytes=400))
        self.assertEqual(second.status_code, 201, second.text)
        self.assertEqual(second.json()["usage_delta_bytes"], 250)
        self.assertTrue(second.json()["quota_exhausted"])
        self.assertEqual(second.json()["used_bytes"], 550)

        # A core restart may reset cumulative counters; new post-reset bytes remain chargeable.
        reset = self.client.post(f"/nodes/{node['id']}/usage/report", headers=headers,
            json=dict(base, event_id="stats-event-reset-1234", event_timestamp=api.now(),
                      upload_bytes=5, download_bytes=7))
        self.assertEqual(reset.status_code, 201, reset.text)
        self.assertEqual(reset.json()["usage_delta_bytes"], 12)
        self.assertEqual(reset.json()["used_bytes"], 562)

        replay = self.client.post(f"/nodes/{node['id']}/usage/report", headers=headers,
            json=dict(base, event_id="stats-event-reset-1234", event_timestamp=api.now(),
                      upload_bytes=5, download_bytes=7))
        self.assertEqual(replay.status_code, 409)

        # Do not allow connection-level counters and aggregate StatsService counters to double-charge.
        mixed_source = self.client.post(f"/nodes/{node['id']}/connections/report", headers=headers,
            json={"event_id": "mixed-source-event-1234", "event_timestamp": api.now(),
                  "user_id": user["id"], "connection_id": "stats-mixed-connection",
                  "node_id": node["id"], "client_ip": "192.0.2.55",
                  "state": "active", "upload_bytes": 10, "download_bytes": 10})
        self.assertEqual(mixed_source.status_code, 409)

        invalid = self.client.post(f"/nodes/{node['id']}/usage/report",
            headers={"Authorization": "Bearer wrong-secret"},
            json=dict(base, event_id="stats-event-invalid-1234"))
        self.assertEqual(invalid.status_code, 401)

    def test_admin_bootstrap_and_server_side_limits(self):
        user = self.register()
        bootstrap = self.client.post("/admin/bootstrap", headers={
            "X-Bootstrap-Token": api.BOOTSTRAP_ADMIN_TOKEN
        })
        self.assertEqual(bootstrap.status_code, 201, bootstrap.text)
        admin_token = self.login()
        limited = self.client.patch(f"/admin/users/{user['id']}/limits",
            headers=admin_token, json={"quota_bytes": 1024, "expires_at": None, "enabled": True})
        self.assertEqual(limited.status_code, 200, limited.text)
        self.assertEqual(limited.json()["quota_bytes"], 1024)
        denied = self.client.post("/admin/users/not-a-user/usage", headers=admin_token,
            json={"node_id": "node-test", "delta_bytes": 20})
        self.assertEqual(denied.status_code, 404)


if __name__ == "__main__":
    unittest.main()
