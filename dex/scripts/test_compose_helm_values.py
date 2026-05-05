#!/usr/bin/env python3
"""Unit tests for compose_helm_values.py — the per-backend overlay composer."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import compose_helm_values as composer  # noqa: E402


SCRIPTS_DIR = Path(__file__).parent
REAL_OVERLAYS_DIR = (SCRIPTS_DIR / ".." / ".." / "api" / "k8s" / "dex").resolve()


class TempOverlayMixin:
    """Helpers for writing throwaway overlay files in each test."""

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.tmpdir = Path(self._tmp.name)
        self.out = self.tmpdir / "merged.yaml"

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write_overlay(self, name: str, body: str) -> Path:
        path = self.tmpdir / name
        path.write_text(body)
        return path


class ExtractConnectorsBlockTests(TempOverlayMixin, unittest.TestCase):
    def test_returns_lines_under_connectors_key(self) -> None:
        path = self.write_overlay(
            "ldap.yaml",
            "config:\n  connectors:\n    - type: ldap\n      id: ldap\n      name: LDAP\n",
        )
        result = composer.extract_connectors_block(path)
        self.assertEqual(
            result, "    - type: ldap\n      id: ldap\n      name: LDAP\n"
        )

    def test_missing_connectors_key_raises(self) -> None:
        path = self.write_overlay(
            "no-connectors.yaml", "config:\n  staticClients: []\n"
        )
        with self.assertRaises(SystemExit) as ctx:
            composer.extract_connectors_block(path)
        self.assertIn("missing 'connectors:'", str(ctx.exception))

    def test_empty_connectors_block_raises(self) -> None:
        path = self.write_overlay("empty.yaml", "config:\n  connectors:\n\n")
        with self.assertRaises(SystemExit) as ctx:
            composer.extract_connectors_block(path)
        self.assertIn("empty", str(ctx.exception))

    def test_strips_trailing_blank_lines_but_preserves_indentation(self) -> None:
        path = self.write_overlay(
            "trailing.yaml",
            "config:\n  connectors:\n    - type: ldap\n      id: ldap\n\n\n",
        )
        result = composer.extract_connectors_block(path)
        self.assertEqual(result, "    - type: ldap\n      id: ldap\n")

    def test_tolerates_header_and_inline_comments(self) -> None:
        path = self.write_overlay(
            "commented.yaml",
            "# Header comment\n"
            "# Another header line\n"
            "config:\n"
            "  connectors:\n"
            "    # inline comment\n"
            "    - type: ldap\n"
            "      id: ldap\n",
        )
        result = composer.extract_connectors_block(path)
        self.assertIn("- type: ldap", result)
        self.assertIn("# inline comment", result)


class CollectConnectorIdsTests(unittest.TestCase):
    def test_single_id(self) -> None:
        block = "    - type: ldap\n      id: ldap\n      name: LDAP\n"
        self.assertEqual(composer.collect_connector_ids(block), ["ldap"])

    def test_multiple_ids_in_order(self) -> None:
        block = (
            "    - type: ldap\n      id: ldap\n"
            "    - type: google\n      id: google\n"
            "    - type: microsoft\n      id: microsoft\n"
        )
        self.assertEqual(
            composer.collect_connector_ids(block), ["ldap", "google", "microsoft"]
        )

    def test_no_ids_returns_empty(self) -> None:
        self.assertEqual(composer.collect_connector_ids("    - type: ldap\n"), [])


class MainTests(TempOverlayMixin, unittest.TestCase):
    def test_single_overlay_produces_wellformed_merged_file(self) -> None:
        a = self.write_overlay(
            "ldap.yaml",
            "config:\n  connectors:\n    - type: ldap\n      id: ldap\n",
        )
        composer.main(["--out", str(self.out), str(a)])
        text = self.out.read_text()
        self.assertTrue(text.startswith("config:\n  connectors:\n"))
        self.assertIn("    - type: ldap", text)
        self.assertIn("      id: ldap", text)

    def test_two_overlays_concatenate_in_argument_order(self) -> None:
        a = self.write_overlay(
            "ldap.yaml",
            "config:\n  connectors:\n    - type: ldap\n      id: ldap\n",
        )
        b = self.write_overlay(
            "google.yaml",
            "config:\n  connectors:\n    - type: google\n      id: google\n",
        )
        composer.main(["--out", str(self.out), str(a), str(b)])
        text = self.out.read_text()
        # Order is preserved by argv order, not filename
        self.assertLess(text.index("id: ldap"), text.index("id: google"))

    def test_duplicate_connector_id_raises(self) -> None:
        a = self.write_overlay(
            "ldap-a.yaml",
            "config:\n  connectors:\n    - type: ldap\n      id: ldap\n",
        )
        b = self.write_overlay(
            "ldap-b.yaml",
            "config:\n  connectors:\n    - type: ldap\n      id: ldap\n",
        )
        with self.assertRaises(SystemExit) as ctx:
            composer.main(["--out", str(self.out), str(a), str(b)])
        msg = str(ctx.exception)
        self.assertIn("Duplicate connector id 'ldap'", msg)
        self.assertIn(str(b), msg)
        self.assertIn(str(a), msg)


class RealOverlayIntegrationTests(TempOverlayMixin, unittest.TestCase):
    """Smoke-test the composer against the actual per-backend overlays we ship.

    Catches regressions where someone adds a key to a real overlay without
    updating the composer (e.g. adds a sibling to `connectors:` under `config:`).
    """

    def test_all_three_overlays_merge_into_three_connectors(self) -> None:
        composer.main(
            [
                "--out",
                str(self.out),
                str(REAL_OVERLAYS_DIR / "values-tls.yaml"),
                str(REAL_OVERLAYS_DIR / "values-google.yaml"),
                str(REAL_OVERLAYS_DIR / "values-microsoft.yaml"),
            ]
        )
        text = self.out.read_text()
        ids = composer.collect_connector_ids(text)
        self.assertEqual(ids, ["ldap", "google", "microsoft"])

    def test_two_ldap_overlays_collide_on_id(self) -> None:
        with self.assertRaises(SystemExit) as ctx:
            composer.main(
                [
                    "--out",
                    str(self.out),
                    str(REAL_OVERLAYS_DIR / "values-tls.yaml"),
                    str(
                        REAL_OVERLAYS_DIR
                        / "values-integration-ldap-plaintext-ephemeral.yaml"
                    ),
                ]
            )
        self.assertIn("Duplicate connector id 'ldap'", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
