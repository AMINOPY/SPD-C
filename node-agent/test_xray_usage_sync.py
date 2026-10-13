import importlib.util
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).parent
sys.path.insert(0, str(ROOT))
MODULE_PATH = ROOT / "xray_usage_sync.py"
spec = importlib.util.spec_from_file_location("spd_con_xray_usage_sync", MODULE_PATH)
sync = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(sync)


class XrayUsageSyncTests(unittest.TestCase):
    def test_parses_xray_user_uplink_and_downlink_counters(self):
        counters = sync.parse_user_counters({
            "user>>>vpn@example.net>>>traffic>>>uplink": 120,
            "user>>>vpn@example.net>>>traffic>>>downlink": 480,
            "user>>>other@example.net>>>traffic>>>uplink": 7,
            "inbound>>>api>>>traffic>>>uplink": 999,
            "user>>>vpn@example.net>>>traffic>>>unknown": 20,
        })
        self.assertEqual(counters["vpn@example.net"],
                         {"upload_bytes": 120, "download_bytes": 480})
        self.assertEqual(counters["other@example.net"],
                         {"upload_bytes": 7, "download_bytes": 0})
        self.assertNotIn("inbound", counters)

    def test_ignores_negative_and_malformed_stats(self):
        self.assertEqual(sync.parse_user_counters({
            "user>>>bad@example.net>>>traffic>>>uplink": -1,
            "user>>>broken>>>traffic>>>uplink>>>extra": 100,
        }), {})

    def test_usage_api_requires_https_outside_localhost(self):
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            sync.post_usage("http://example.net", "node", "secret", "user",
                            {"upload_bytes": 1, "download_bytes": 2})


if __name__ == "__main__":
    unittest.main()
