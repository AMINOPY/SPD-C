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
