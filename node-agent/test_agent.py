import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("agent.py")
spec = importlib.util.spec_from_file_location("spd_con_node_agent", MODULE_PATH)
agent = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(agent)


class NodeAgentTests(unittest.TestCase):
    def test_normalizes_ipv4_and_defaults(self):
        event = agent.normalize_event({
            "user_id": "user-123", "connection_id": "connection-123",
            "client_ip": "192.0.2.8"
        }, "node-test")
        self.assertEqual(event["client_ip"], "192.0.2.8")
        self.assertEqual(event["state"], "active")
        self.assertEqual(event["upload_bytes"], 0)
        self.assertEqual(event["node_id"], "node-test")

    def test_normalizes_ipv6(self):
        event = agent.normalize_event({
            "user_id": "user-123", "connection_id": "connection-123",
            "client_ip": "2001:db8::1", "state": "disconnected"
        }, "node-test")
        self.assertEqual(event["client_ip"], "2001:db8::1")
        self.assertEqual(event["state"], "disconnected")

    def test_rejects_invalid_ip_and_negative_counters(self):
        base = {"user_id": "user-123", "connection_id": "connection-123",
                "client_ip": "not-an-ip"}
        with self.assertRaises(ValueError):
            agent.normalize_event(base, "node-test")
        base["client_ip"] = "192.0.2.1"
        base["upload_bytes"] = -1
        with self.assertRaises(ValueError):
            agent.normalize_event(base, "node-test")

    def test_core_identity_maps_to_account_case_insensitively(self):
        mapping = {"vpn-user@example.net": "spd-user-42"}
        event = agent.normalize_event({
            "core_email": "VPN-USER@EXAMPLE.NET",
            "connection_id": "connection-identity-1",
            "client_ip": "192.0.2.44",
        }, "node-test", mapping)
        self.assertEqual(event["user_id"], "spd-user-42")

    def test_unknown_core_identity_is_not_guessed_from_ip(self):
        with self.assertRaisesRegex(ValueError, "not mapped"):
            agent.normalize_event({
                "core_email": "unknown@example.net",
                "connection_id": "connection-identity-2",
                "client_ip": "192.0.2.44",
            }, "node-test", {"known@example.net": "spd-user-42"})

    def test_identity_mapping_rejects_conflicting_user_id(self):
        with self.assertRaisesRegex(ValueError, "conflicts"):
            agent.normalize_event({
                "core_email": "known@example.net",
                "user_id": "different-user",
                "connection_id": "connection-identity-3",
                "client_ip": "192.0.2.44",
            }, "node-test", {"known@example.net": "spd-user-42"})

    def test_identity_map_loader_validates_json_mapping(self):
        import json
        import tempfile
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8") as file:
            json.dump({"VPN@example.net": "user-1"}, file)
            file.flush()
            mapping = agent.load_identity_map(file.name)
        self.assertEqual(mapping, {"vpn@example.net": "user-1"})

    def test_public_api_requires_https(self):
        event = {"event_id": "event-123456789012", "event_timestamp": 1}
        with self.assertRaises(ValueError):
            agent.post_event("http://example.com", "node-test", "secret", event)


if __name__ == "__main__":
    unittest.main()
