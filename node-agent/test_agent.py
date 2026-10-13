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

    def test_public_api_requires_https(self):
        event = {"event_id": "event-123456789012", "event_timestamp": 1}
        with self.assertRaises(ValueError):
            agent.post_event("http://example.com", "node-test", "secret", event)


if __name__ == "__main__":
    unittest.main()
