import importlib.util
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = pathlib.Path(__file__).with_name("xray_runtime.py")
spec = importlib.util.spec_from_file_location("spd_con_xray_runtime", MODULE_PATH)
runtime = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(runtime)


class XrayRuntimeTests(unittest.TestCase):
    def test_parse_stats_output_ignores_non_stat_lines(self):
        parsed = runtime.parse_stats_output(
            "stat: ok\nname: user>>>alice>>>traffic>>>uplink value: 120\n"
            "name: user>>>alice>>>traffic>>>downlink value: 456\n"
        )
        self.assertEqual(parsed, {
            "user>>>alice>>>traffic>>>uplink": 120,
            "user>>>alice>>>traffic>>>downlink": 456,
        })

    def test_stats_api_must_be_loopback(self):
        ok, message = runtime.query_stats("xray", "0.0.0.0:10085")
        self.assertFalse(ok)
        self.assertIn("loopback", message)

    def test_stats_api_invokes_xray_cli_for_loopback(self):
        fake = subprocess.CompletedProcess(args=["xray"], returncode=0,
            stdout="name: user>>>alice>>>traffic>>>uplink value: 42\n", stderr="")
        with patch.object(runtime.subprocess, "run", return_value=fake) as run:
            ok, stats = runtime.query_stats("xray", "127.0.0.1:10085", "user>>>")
        self.assertTrue(ok)
        self.assertEqual(stats["user>>>alice>>>traffic>>>uplink"], 42)
        self.assertEqual(run.call_args.args[0][1:3], ["api", "statsquery"])

    def test_validate_config_rejects_missing_file(self):
        ok, message = runtime.validate_config("xray", "/definitely/not/a/config.json")
        self.assertFalse(ok)
        self.assertIn("does not exist", message)

    def test_validate_config_does_not_echo_engine_stderr(self):
        with tempfile.NamedTemporaryFile("w", suffix=".json") as config:
            config.write("{}")
            config.flush()
            fake = subprocess.CompletedProcess(args=["xray"], returncode=1,
                stdout="", stderr="private-secret-from-engine-log")
            with patch.object(runtime.subprocess, "run", return_value=fake):
                ok, message = runtime.validate_config("xray", config.name)
        self.assertFalse(ok)
        self.assertNotIn("private-secret", message)


if __name__ == "__main__":
    unittest.main()
