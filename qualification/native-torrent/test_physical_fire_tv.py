"""Safety and evidence regressions; these never address a real device."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import physical_fire_tv as physical


def successful_output(names):
    return "\n".join(f"INSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: 0" for name in names) + f"\nOK ({len(names)} tests)\nINSTRUMENTATION_CODE: -1\n"


class PhysicalQualificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=physical.ROOT / ".scratch/native-torrent")
        self.directory = Path(self.temp.name)
        self.q = physical.Qualification(self.directory, {"ANDROID_ADB_BIN": "/unused/adb",
                                       "VIPTV_TAILSCALE_TV_ADB_SERIAL": "owned-device.invalid:5555"})

    def tearDown(self):
        self.temp.cleanup()

    def test_private_output_cannot_escape_through_a_directory_symlink(self):
        with tempfile.TemporaryDirectory() as outside:
            link = self.directory / "redirect"
            link.symlink_to(outside, target_is_directory=True)
            with self.assertRaises(RuntimeError):
                physical.private_directory(link / "run")
            with self.assertRaises(RuntimeError):
                physical.write_private(link / "evidence.json", "private")
            self.assertEqual(list(Path(outside).iterdir()), [])

    def test_hard_link_cannot_truncate_another_file(self):
        original = self.directory / "original"
        original.write_text("preserve")
        target = self.directory / "evidence"
        target.hardlink_to(original)
        with self.assertRaises(RuntimeError):
            physical.write_private(target, "replaced")
        self.assertEqual(original.read_text(), "preserve")

    def test_emulator_in_private_configuration_is_rejected(self):
        (self.directory / ".env").write_text('ANDROID_ADB_BIN="/unused/adb"\nVIPTV_TAILSCALE_TV_ADB_SERIAL="emulator-5554"\n')
        with self.assertRaises(RuntimeError):
            physical.configured_values(self.directory)

    def test_mismatched_apk_hash_causes_no_device_command(self):
        apk = self.directory / "app.apk"
        apk.write_bytes(b"untrusted APK")
        with patch.object(self.q, "command") as command:
            with self.assertRaises(RuntimeError):
                self.q.install(apk, "0" * 64, physical.NORMAL)
            command.assert_not_called()

    def test_wrong_physical_model_cannot_receive_remote_input(self):
        replies = {"device-state": b"device", "device-model": b"other", "device-sdk": b"30", "device-abi": b"armeabi-v7a"}
        with patch.object(self.q, "command", side_effect=lambda label, *_: replies[label]) as command:
            with self.assertRaises(RuntimeError):
                self.q.key("KEYCODE_DPAD_CENTER")
            self.assertFalse(any(call.args[0].startswith("remote-") for call in command.call_args_list))

    def test_owner_app_foreground_blocks_fixture_input(self):
        with patch.object(self.q, "device"), patch.object(self.q, "command", return_value=b"mResumedActivity: org.viptv.app/.MainActivity") as command:
            with self.assertRaises(RuntimeError):
                self.q.key("KEYCODE_BACK")
            self.assertEqual([call.args[0] for call in command.call_args_list], ["foreground"])

    def test_any_reverse_conflict_blocks_all_mapping_mutations(self):
        with patch.object(self.q, "device"), patch.object(self.q, "command", return_value=b"owned tcp:19446 tcp:29999") as command:
            with self.assertRaises(RuntimeError):
                self.q.fixture("org.viptv.app.OwnedNativePipelineTest")
            self.assertEqual([call.args[0] for call in command.call_args_list], ["reverse-before"])

    def test_sound_and_physical_remote_cannot_be_inferred_from_events(self):
        (self.directory / "events.json").write_text('{"selected_audio":true,"position_advanced":true}')
        for case in ["audible_audio", "physical_remote_focus"]:
            with self.assertRaises(RuntimeError):
                self.q.record(case, "PASS", "instrumentation", "events.json")
        self.assertFalse((self.directory / "cases").exists())

    def test_skipped_jni_cannot_pass_from_summary_alone(self):
        output = successful_output(["cleartextIsRestrictedToLiteralIpv4Loopback"])
        output += "INSTRUMENTATION_STATUS: test=actualGeneratedFacadesLoadWithoutReplacingCoreOrJna\nINSTRUMENTATION_STATUS_CODE: -4\n"
        self.assertFalse(physical.instrumentation_passed(output, 2))
        self.assertFalse(physical.instrumentation_passed("OK (2 tests)\nINSTRUMENTATION_CODE: -1", 2))

    def test_only_declared_manual_observation_skip_is_accepted(self):
        output = successful_output(["ownedPipeline"])
        output += "INSTRUMENTATION_STATUS: test=manualOwnedNativeObservation\nINSTRUMENTATION_STATUS_CODE: -4\n"
        self.assertTrue(physical.instrumentation_passed(output, allowed_skips=("manualOwnedNativeObservation",)))
        self.assertFalse(physical.instrumentation_passed(output))
        self.assertFalse(physical.instrumentation_passed(output.replace("manualOwnedNativeObservation", "decodedVideo"), allowed_skips=("manualOwnedNativeObservation",)))

    def test_failure_cannot_pass_with_a_misleading_success_summary(self):
        output = successful_output(["ownedPipeline"]) + "INSTRUMENTATION_STATUS: test=cleanup\nINSTRUMENTATION_STATUS_CODE: -2\n"
        self.assertFalse(physical.instrumentation_passed(output))

    def test_human_verdict_keeps_evidence_hash_and_remains_separate(self):
        proof = self.directory / "human-confirmation.json"
        proof.write_text('{"synthetic_unit_fixture_only":true,"heard_actual_tv":true}')
        self.q.record("audible_audio", "PASS", "human", proof.name, "human")
        verdict = json.loads((self.directory / "cases/audible_audio.json").read_text())
        self.assertEqual(verdict["evidence_sha256"], hashlib.sha256(proof.read_bytes()).hexdigest())
        self.assertFalse((self.directory / "cases/exact_file_decoded_video.json").exists())


if __name__ == "__main__":
    unittest.main()
