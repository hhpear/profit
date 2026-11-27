#!/usr/bin/env python3
"""Send the independent enterprise-profit alert through bmp-task-bot and read it back."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]


def read(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def compact(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def sha_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def run_lark(args: list[str], profile: str) -> dict[str, Any]:
    completed = subprocess.run(
        ["lark-cli", *args, "--profile", profile, "--as", "bot", "--format", "json"],
        cwd=ROOT, text=True, capture_output=True,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "lark-cli failed")
    result = json.loads(completed.stdout)
    if result.get("ok") is not True:
        raise RuntimeError("lark-cli returned non-success")
    return result.get("data") or {}


def resolve_email(profile: str, email: str) -> str:
    data = run_lark([
        "api", "POST", "/open-apis/contact/v3/users/batch_get_id",
        "--params", compact({"user_id_type": "open_id"}),
        "--data", compact({"emails": [email], "include_resigned": True}),
    ], profile)
    users = data.get("user_list") or []
    matches = [item for item in users if item.get("email") == email and item.get("user_id")]
    if len(matches) != 1:
        raise RuntimeError(f"recipient email did not resolve uniquely under {profile}: {email}")
    return str(matches[0]["user_id"])


def load_rules(rules_arg: Path | None, acceptance_test: bool) -> tuple[dict[str, Any], Path, str]:
    active_path = ROOT / "config/enterprise-profit-alert-rules/active.json"
    active = read(active_path)
    if rules_arg is not None:
        if not acceptance_test:
            raise RuntimeError("non-active rules are allowed only for acceptance test")
        rules_path = rules_arg.resolve()
        rules_root = (ROOT / "config/enterprise-profit-alert-rules").resolve()
        if rules_path.parent != rules_root:
            raise RuntimeError("acceptance rules must be an enterprise-profit-alert ruleset")
    else:
        rules_path = ROOT / active["ruleset_path"]
    actual_sha = hashlib.sha256(rules_path.read_bytes()).hexdigest()
    rules = read(rules_path)
    if rules_arg is None:
        if actual_sha != active.get("ruleset_sha256"):
            raise RuntimeError("active enterprise-profit alert rules hash mismatch")
        if rules.get("version") != active.get("active_version"):
            raise RuntimeError("active enterprise-profit alert rules version mismatch")
    elif rules.get("status") not in {"CANDIDATE", "APPROVED", "ACTIVE"}:
        raise RuntimeError("acceptance ruleset status is not allowed")
    return rules, rules_path, actual_sha


def decode(value: object) -> object:
    if not isinstance(value, str):
        return value
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return value


def walk(value: object):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def select_delivery(delivery: dict[str, Any], recipient_name: str | None,
                    acceptance_test: bool, manual_resend: bool) -> tuple[list[dict[str, Any]], str, str]:
    if acceptance_test and manual_resend:
        raise RuntimeError("acceptance test and manual resend are mutually exclusive")
    recipients = delivery["recipients"]
    if recipient_name:
        if acceptance_test:
            if recipient_name != delivery.get("acceptance_test_recipient"):
                raise RuntimeError("acceptance-test recipient is not approved")
            mode = "acceptance-test"
        elif manual_resend:
            mode = "manual-resend"
        else:
            raise RuntimeError("single-recipient delivery requires an explicit approved mode")
        recipients = [item for item in recipients if item.get("name") == recipient_name]
        if len(recipients) != 1:
            raise RuntimeError("single-recipient delivery target is not uniquely allowlisted")
        receipt_suffix = "acceptance-test" if acceptance_test else f"{mode}-{recipient_name}"
        return recipients, mode, receipt_suffix
    if acceptance_test or manual_resend:
        raise RuntimeError("single-recipient mode requires --recipient-name")
    if not recipients:
        raise RuntimeError("delivery recipient allowlist is empty")
    return recipients, "production", "production"


def validate_card_contract(alert: dict[str, Any], card: dict[str, Any],
                           rules: dict[str, Any], acceptance_test: bool = False) -> None:
    if alert.get("status") != "SUCCESS":
        raise RuntimeError("profit alert artifact is not successful")
    if alert.get("ruleset_version") != rules.get("version"):
        raise RuntimeError("alert artifact ruleset mismatch")
    if card.get("schema") != "2.0":
        raise RuntimeError("alert card is not Card 2.0")
    payload = compact(card)
    if alert.get("alert_count", 0) < 1:
        if not acceptance_test:
            raise RuntimeError("no publishable profit anomaly exists")
        if rules.get("delivery", {}).get("send_when_no_anomaly") is not False:
            raise RuntimeError("zero-alert acceptance requires production no-send policy")
        if "本次没有满足新规则的业务告警" not in payload:
            raise RuntimeError("zero-alert acceptance card summary is missing")
        if any(item.get("tag") == "chart" for item in walk(card)):
            raise RuntimeError("zero-alert acceptance card must not contain charts")
        return
    for field in rules["display"]["fields"]:
        if field not in payload:
            raise RuntimeError(f"alert card is missing required field: {field}")
    if rules["display"]["trend_title"] not in payload:
        raise RuntimeError("alert card is missing trend title")
    elements = list(walk(card))
    charts = [item for item in elements if item.get("tag") == "chart"]
    if len(charts) != alert["alert_count"]:
        raise RuntimeError("alert card trend chart count mismatch")
    for chart in charts:
        element_id = str(chart.get("element_id") or "")
        if alert.get("schema_version") == "2.0":
            expected_color = "red"
        else:
            expected_color = "red" if element_id.startswith("profit_decrease_") else "green"
        spec = chart.get("chart_spec") or {}
        if (spec.get("type") != "line" or spec.get("color") != [expected_color]
                or len((spec.get("data") or {}).get("values") or []) != 15):
            raise RuntimeError(f"alert trend chart contract mismatch: {element_id}")


def delivery_context(alert: dict[str, Any]) -> dict[str, Any]:
    if alert.get("schema_version") == "2.0":
        return {"current_day": alert["current_day"],
                "baseline_method": alert["baseline_method"]}
    return {"current_day": alert["current_day"], "comparison_day": alert["comparison_day"]}


def rendered_readback_markers(alert: dict[str, Any], rules: dict[str, Any],
                              acceptance_test: bool) -> list[str]:
    if alert.get("alert_count", 0) == 0 and acceptance_test:
        return [rules["display"]["title"], "业务告警", "利润上升观察",
                "本次没有满足新规则的业务告警"]
    if alert.get("schema_version") == "2.0":
        return [rules["display"]["title"], "需要处理", "实际利润", "基线利润",
                "利润偏差额", "利润偏差率", rules["display"]["trend_title"]]
    return [rules["display"]["title"], "利润下降", "利润上升",
            "利润变化额", "利润变化率", "昨日利润", "对比利润",
            rules["display"]["trend_title"]]


def validate_sender_identity(sender: dict[str, Any], rules: dict[str, Any]) -> None:
    delivery = rules["delivery"]
    sender_id = str(sender.get("id") or "")
    sender_name = str(sender.get("name") or "")
    if (sender_id != str(delivery.get("sender_app_id") or "")
            or sender_name != str(delivery.get("sender_name") or "")):
        raise RuntimeError("sender identity mismatch")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--recipient-name")
    parser.add_argument("--acceptance-test", action="store_true")
    parser.add_argument("--manual-resend", action="store_true")
    parser.add_argument("--rules", type=Path)
    args = parser.parse_args()
    directory = args.input_dir.resolve()
    rules, rules_path, rules_sha = load_rules(args.rules, args.acceptance_test)
    alert_path = directory / "enterprise-profit-alert.json"
    card_path = directory / "enterprise-profit-alert-card.json"
    alert = read(alert_path)
    card = read(card_path)
    validate_card_contract(alert, card, rules, acceptance_test=args.acceptance_test)
    payload = compact(card)
    delivery = rules["delivery"]
    profile = delivery["profile"]
    recipients, mode, receipt_suffix = select_delivery(
        delivery, args.recipient_name, args.acceptance_test, args.manual_resend)
    receipt_path = directory / f"enterprise-profit-alert-delivery-{receipt_suffix}.json"
    if receipt_path.exists() and read(receipt_path).get("status") == "SUCCESS":
        raise RuntimeError("idempotency gate: this alert delivery already succeeded")
    card_sha = sha_text(payload)
    intended = []
    for recipient in recipients:
        email = recipient["email"]
        user_id = resolve_email(profile, email)
        material = "\x1f".join((rules["version"], alert["current_day"], card_sha, email, mode))
        key = "epa-" + hashlib.sha256(material.encode("utf-8")).hexdigest()[:46]
        intended.append({**recipient, "user_id": user_id, "idempotency_key": key})
    messages = []
    for recipient in intended:
        sent = run_lark([
            "im", "+messages-send", "--user-id", recipient["user_id"],
            "--msg-type", "interactive", "--content", payload,
            "--idempotency-key", recipient["idempotency_key"],
        ], profile)
        if not sent.get("message_id") or not sent.get("chat_id"):
            raise RuntimeError(f"message send response is incomplete: {recipient['name']}")
        messages.append({**recipient, "message_id": sent["message_id"], "chat_id": sent["chat_id"]})
    fetched = run_lark([
        "im", "+messages-mget", "--message-ids",
        ",".join(item["message_id"] for item in messages), "--no-reactions",
    ], profile)
    by_id = {item["message_id"]: item for item in fetched.get("messages") or []}
    if set(by_id) != {item["message_id"] for item in messages}:
        raise RuntimeError("message readback is incomplete")
    readbacks = []
    for sent in messages:
        message = by_id[sent["message_id"]]
        if message.get("msg_type") != "interactive" or message.get("chat_id") != sent["chat_id"]:
            raise RuntimeError("message readback type/chat mismatch")
        sender = message.get("sender") or {}
        if not (sender.get("id") or sender.get("open_bot_id") or sender.get("open_id")):
            raise RuntimeError("message sender readback is missing")
        validate_sender_identity(sender, rules)
        content = decode(message.get("content"))
        if isinstance(content, dict):
            if content != card:
                raise RuntimeError("message Card JSON readback mismatch")
            content_mode = "CARD_JSON"
        elif isinstance(content, str):
            required = rendered_readback_markers(alert, rules, args.acceptance_test)
            if not content.startswith("<card ") or any(marker not in content for marker in required):
                raise RuntimeError("message rendered Card readback marker mismatch")
            content_mode = "RENDERED_CARD_TEXT"
        else:
            raise RuntimeError("message readback content type mismatch")
        readbacks.append({
            "recipient_name": sent["name"], "recipient_email": sent["email"],
            "message_id": sent["message_id"], "chat_id": sent["chat_id"],
            "msg_type": message["msg_type"], "sender": sender,
            "content_mode": content_mode,
            "readback_content_sha256": sha_text(compact(content) if isinstance(content, dict) else content),
        })
    receipt = {
        "schema_version": "1.0", "status": "SUCCESS", "delivery_mode": mode,
        "profile": profile, "identity": "bot", "ruleset_version": rules["version"],
        "ruleset_path": str(rules_path), "ruleset_sha256": rules_sha,
        **delivery_context(alert),
        "alert_count": alert["alert_count"], "card_path": str(card_path),
        "card_compact_sha256": card_sha, "recipient_count": len(readbacks),
        "messages": readbacks, "message_readback_confirmed": True,
    }
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "receipt": str(receipt_path),
                      "recipient_count": len(readbacks),
                      "message_ids": [item["message_id"] for item in readbacks]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
