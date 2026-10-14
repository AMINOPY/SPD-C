import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = pathlib.Path(__file__).with_name("xray_provision.py")
spec = importlib.util.spec_from_file_location("spd_con_xray_provision", MODULE_PATH)
provision = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(provision)

CLIENT_ID = "d9428888-122b-4f4e-8a06-2a3a5f10b001"
OTHER_ID = "d9428888-122b-4f4e-8a06-2a3a5f10b002"


def sample_config():
    return {
        "inbounds": [{
            "tag": "vless-in",
            "listen": "127.0.0.1",
            "port": 12345,
            "protocol": "vless",
            "settings": {"clients": [{"id": OTHER_ID, "email": "existing@spdcon.local"}],
                         "decryption": "none"},
            "streamSettings": {"network": "tcp", "security": "none"},
        }],
        "outbounds": [{"protocol": "freedom", "tag": "direct"}],
    }


class XrayProvisionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = pathlib.Path(self.temp.name) / "config.json"
        self.path.write_text(json.dumps(sample_config()), encoding="utf-8")

    def test_add_is_dry_run_by_default(self):
        before = self.path.read_text(encoding="utf-8")
        with patch.object(provision, "validate_candidate", return_value=(True, "ok")):
            result = provision.change_client(str(self.path), "vless-in", CLIENT_ID,
                                             "user-123@spdcon.local", "add")
        self.assertTrue(result["ok"])
        self.assertFalse(result["applied"])
        self.assertEqual(self.path.read_text(encoding="utf-8"), before)

    def test_apply_validates_then_atomically_adds_client(self):
        with patch.object(provision, "validate_candidate", return_value=(True, "ok")) as validate:
            result = provision.change_client(str(self.path), "vless-in", CLIENT_ID,
                                             "user-123@spdcon.local", "add", apply=True)
        self.assertTrue(result["applied"])
        validate.assert_called_once()
        updated = json.loads(self.path.read_text(encoding="utf-8"))
        clients = updated["inbounds"][0]["settings"]["clients"]
        self.assertEqual(len(clients), 2)
        self.assertEqual(clients[1]["id"], CLIENT_ID)
        self.assertEqual(clients[1]["email"], "user-123@spdcon.local")

    def test_invalid_candidate_never_replaces_config(self):
        before = self.path.read_text(encoding="utf-8")
        with patch.object(provision, "validate_candidate", return_value=(False, "invalid")):
            result = provision.change_client(str(self.path), "vless-in", CLIENT_ID,
                                             "user-123@spdcon.local", "add", apply=True)
        self.assertFalse(result["ok"])
        self.assertEqual(self.path.read_text(encoding="utf-8"), before)

    def test_concurrent_config_edit_is_not_overwritten(self):
        concurrent_config = sample_config()
        concurrent_config["outbounds"].append({"protocol": "blackhole", "tag": "concurrent-edit"})
        concurrent_text = json.dumps(concurrent_config)

        def edit_during_validation(_xray_bin, _candidate_path):
            self.path.write_text(concurrent_text, encoding="utf-8")
            return True, "candidate valid"

        with patch.object(provision, "validate_candidate", side_effect=edit_during_validation):
            with self.assertRaisesRegex(ValueError, "concurrent edits"):
                provision.change_client(str(self.path), "vless-in", CLIENT_ID,
                                        "user-123@spdcon.local", "add", apply=True)
        self.assertEqual(self.path.read_text(encoding="utf-8"), concurrent_text)

    def test_exact_add_is_idempotent(self):
        config = sample_config()
        config, changed = provision.mutate_vless_client(
            config, "vless-in", CLIENT_ID, "user-123@spdcon.local", "add")
        self.assertTrue(changed)
        config, changed = provision.mutate_vless_client(
            config, "vless-in", CLIENT_ID, "user-123@spdcon.local", "add")
        self.assertFalse(changed)
        self.assertEqual(len(config["inbounds"][0]["settings"]["clients"]), 2)

    def test_conflicting_uuid_or_email_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "already belongs"):
            provision.mutate_vless_client(sample_config(), "vless-in", OTHER_ID,
                                           "new@spdcon.local", "add")
        with self.assertRaisesRegex(ValueError, "already belongs"):
            provision.mutate_vless_client(sample_config(), "vless-in", CLIENT_ID,
                                           "existing@spdcon.local", "add")

    def test_remove_requires_matching_uuid_and_email(self):
        with self.assertRaisesRegex(ValueError, "does not match"):
            provision.mutate_vless_client(sample_config(), "vless-in", OTHER_ID,
                                           "someone-else@spdcon.local", "remove")
        config, changed = provision.mutate_vless_client(
            sample_config(), "vless-in", OTHER_ID, "existing@spdcon.local", "remove")
        self.assertTrue(changed)
        self.assertEqual(config["inbounds"][0]["settings"]["clients"], [])

    def test_rejects_non_vless_and_ambiguous_inbounds(self):
        config = sample_config()
        config["inbounds"][0]["protocol"] = "trojan"
        with self.assertRaisesRegex(ValueError, "VLESS"):
            provision.mutate_vless_client(config, "vless-in", CLIENT_ID,
                                           "user-123@spdcon.local", "add")
        config = sample_config()
        config["inbounds"].append(dict(config["inbounds"][0]))
        with self.assertRaisesRegex(ValueError, "Exactly one"):
            provision.mutate_vless_client(config, "vless-in", CLIENT_ID,
                                           "user-123@spdcon.local", "add")

    def test_rejects_symlink_config(self):
        link = pathlib.Path(self.temp.name) / "link.json"
        link.symlink_to(self.path)
        with self.assertRaisesRegex(ValueError, "symlink"):
            provision.load_config(link)

    def test_requires_valid_uuid(self):
        with self.assertRaisesRegex(ValueError, "UUID"):
            provision.mutate_vless_client(sample_config(), "vless-in", "not-a-uuid",
                                           "user-123@spdcon.local", "add")


if __name__ == "__main__":
    unittest.main()
