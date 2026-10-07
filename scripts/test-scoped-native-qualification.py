"""Exercise receipt admission against committed, isolated normal build inputs."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest


spec = importlib.util.spec_from_file_location(
    "scoped_qualification", Path(__file__).with_name("prepare-scoped-native-qualification.py")
)
qualification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualification)


class ScopedQualificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "output"
        self.receipt = self.root / "receipt.json"
        for name in ("CORE_REF", "TORRENT_REF", "DESIGN_REF"):
            (self.root / name).write_text("a" * 40 + "\n")
        (self.root / "app").mkdir()
        (self.root / "app/AGENTS.md").write_text("Owner guide\n")
        paths = ["vendor/playback-gateway/lock.json", "vendor/core/lock.json"]
        for abi in ("armeabi-v7a", "x86_64"):
            paths.extend([
                f"vendor/playback-gateway/ffi/generated/android/jniLibs/{abi}/libplayback_gateway_ffi.so",
                f"app/src/androidMain/jniLibs/{abi}/libviptv_core.so",
            ])
        for name in paths:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        self.git("init", "-q")
        self.git("add", ".")
        self.git("-c", "user.name=Receipt test", "-c", "user.email=receipt@example.invalid", "commit", "-qm", "normal inputs")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def measured(self, platform):
        abi = platform["processAbi"]
        digest = qualification.sha
        return {
            "version": 1, "decision": qualification.DECISION,
            "sourceRevision": self.git("rev-parse", "HEAD"),
            "normalApkSha256": "b" * 64,
            "qualification": {"normalJni": "pass", "ownedMedia3": "pass", "evidenceSha256": "c" * 64},
            "platform": dict(platform),
            "cohort": {
                "coreRevision": "a" * 40, "torrentRevision": "a" * 40, "designRevision": "a" * 40,
                "gatewayLockSha256": digest(self.root / "vendor/playback-gateway/lock.json"),
                "coreLockSha256": digest(self.root / "vendor/core/lock.json"),
                "gatewayLibrarySha256": digest(self.root / f"vendor/playback-gateway/ffi/generated/android/jniLibs/{abi}/libplayback_gateway_ffi.so"),
                "coreLibrarySha256": digest(self.root / f"app/src/androidMain/jniLibs/{abi}/libviptv_core.so"),
            },
        }

    def prepare(self, receipt):
        self.receipt.write_text(json.dumps(receipt))
        qualification.prepare(self.root, self.receipt, self.output)

    def test_each_measured_target_uses_its_own_library_cohort(self):
        for target in qualification.MEASURED_TARGETS:
            with self.subTest(target=target):
                receipt = self.measured(target)
                self.prepare(receipt)
                self.assertEqual(json.loads((self.output / "native-torrent/scoped-qualification.json").read_text()), receipt)

    def test_other_target_and_cross_abi_evidence_are_refused(self):
        receipt = self.measured(qualification.MEASURED_TARGETS[1])
        receipt["platform"]["apiLevel"] = 35
        with self.assertRaisesRegex(ValueError, "measured"):
            self.prepare(receipt)
        receipt = self.measured(qualification.MEASURED_TARGETS[1])
        receipt["cohort"] = self.measured(qualification.MEASURED_TARGETS[0])["cohort"]
        with self.assertRaisesRegex(ValueError, "cohort"):
            self.prepare(receipt)

    def test_missing_actual_pipeline_pass_is_refused(self):
        receipt = self.measured(qualification.MEASURED_TARGETS[1])
        receipt["qualification"]["ownedMedia3"] = "not-run"
        with self.assertRaisesRegex(ValueError, "Actual"):
            self.prepare(receipt)

    def test_absent_receipt_removes_previous_enabling_asset(self):
        self.prepare(self.measured(qualification.MEASURED_TARGETS[1]))
        qualification.prepare(self.root, None, self.output)
        self.assertFalse((self.output / "native-torrent/scoped-qualification.json").exists())

    def test_modified_owner_guide_is_recorded_but_code_changes_refused(self):
        (self.root / "app/AGENTS.md").write_text("Owner's edited guide\n")
        self.prepare(self.measured(qualification.MEASURED_TARGETS[1]))
        emitted = json.loads((self.output / "native-torrent/scoped-qualification.json").read_text())
        self.assertEqual(emitted["documentationOnlyChanges"], ["app/AGENTS.md"])
        (self.root / "app/uncommitted.kt").write_text("val changed = true\n")
        with self.assertRaisesRegex(ValueError, "committed"):
            self.prepare(self.measured(qualification.MEASURED_TARGETS[1]))


if __name__ == "__main__":
    unittest.main()
