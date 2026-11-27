#!/usr/bin/env python3
"""Activate an accepted immutable enterprise-profit alert ruleset."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ACTIVE = ROOT / "config/enterprise-profit-alert-rules/active.json"
CANONICAL_DOC = ROOT / "docs/企业客户利润异常告警.md"


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def atomic_write(path: Path, content: str) -> None:
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--confirm-version", required=True)
    parser.add_argument("--acceptance-receipt", type=Path, required=True)
    parser.add_argument("--change-id", required=True)
    parser.add_argument("--approved-by", required=True)
    parser.add_argument("--reason", required=True)
    parser.add_argument("--effective-at", required=True)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    require(args.version == args.confirm_version, "--confirm-version must exactly match --version")
    effective_at = datetime.fromisoformat(args.effective_at)
    require(effective_at.tzinfo is not None and effective_at.utcoffset() is not None,
            "--effective-at must be an ISO-8601 timestamp with timezone")
    current = read(ACTIVE)
    current_path = ROOT / current["ruleset_path"]
    require(sha(current_path) == current.get("ruleset_sha256"), "current rules pointer/hash mismatch")

    candidate_path = ROOT / f"config/enterprise-profit-alert-rules/{args.version}.json"
    require(candidate_path.is_file(), "candidate ruleset does not exist")
    candidate = read(candidate_path)
    digest = sha(candidate_path)
    require(candidate.get("ruleset_id") == "enterprise-profit-alert", "candidate ruleset id mismatch")
    require(candidate.get("version") == args.version, "candidate version mismatch")
    require(candidate.get("status") == "APPROVED", "ruleset is not an approved immutable ruleset")

    receipt_path = args.acceptance_receipt.resolve()
    receipt = read(receipt_path)
    require(receipt.get("status") == "SUCCESS", "acceptance receipt is not successful")
    require(receipt.get("ruleset_version") == args.version, "receipt ruleset version mismatch")
    require(receipt.get("ruleset_sha256") == digest, "receipt ruleset hash mismatch")
    if receipt.get("delivery_mode") == "acceptance-test":
        require(receipt.get("message_readback_confirmed") is True,
                "message readback is not confirmed")
        require(receipt.get("recipient_count") == 1,
                "acceptance receipt must have one recipient")
        require((receipt.get("messages") or [{}])[0].get("recipient_name") ==
                candidate["delivery"]["acceptance_test_recipient"],
                "acceptance recipient mismatch")
    else:
        require(receipt.get("delivery_mode") == "governance-hardening",
                "receipt is not an accepted activation evidence mode")
        require((candidate.get("acceptance") or {}).get("activation_evidence_mode") ==
                "GOVERNANCE_HARDENING_WITH_EXISTING_DELIVERY_READBACK",
                "candidate does not authorize governance-hardening evidence")
        require(receipt.get("send_attempted") is False
                and receipt.get("message_count") == 0,
                "governance-hardening activation must not send messages")
        require(receipt.get("candidate_validation") == "SUCCESS",
                "candidate governance validation did not succeed")
        previous_delivery = Path(str(receipt.get("previous_delivery_readback") or ""))
        require(previous_delivery.is_file(), "previous delivery readback is missing")
        previous = read(previous_delivery)
        require(previous.get("status") == "SUCCESS"
                and previous.get("message_readback_confirmed") is True,
                "previous delivery readback is not confirmed")

    document = CANONICAL_DOC.read_text(encoding="utf-8")
    require(f"<!-- ACTIVE_RULESET_VERSION: {args.version} -->" in document,
            "canonical document version marker mismatch")
    require(f"<!-- ACTIVE_RULESET_SHA256: {digest} -->" in document,
            "canonical document hash marker mismatch")

    pointer = {
        "schema_version": "1.0",
        "ruleset_id": "enterprise-profit-alert",
        "status": "ACTIVE",
        "active_version": args.version,
        "ruleset_path": str(candidate_path.relative_to(ROOT)),
        "ruleset_sha256": digest,
        "activation_id": f"activate-{args.change_id}-{args.version}",
        "activated_at": effective_at.isoformat(),
        "approved_by": args.approved_by,
        "reason": args.reason,
        "previous_version": (current.get("previous_version") if current.get("active_version") == args.version
                             else current.get("active_version")),
        "acceptance_receipt": str(receipt_path),
    }
    if not args.dry_run:
        atomic_write(ACTIVE, json.dumps(pointer, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": "DRY_RUN" if args.dry_run else "SUCCESS",
                      "active_pointer": pointer}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
