#!/usr/bin/env python3
"""Render the enterprise profit alert as a deterministic Feishu Card 2.0."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path


MONEY = Decimal("0.01")
RATE = Decimal("0.01")


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def signed_money(value: object) -> str:
    number = Decimal(str(value)).quantize(MONEY, rounding=ROUND_HALF_UP)
    return f"{number:+,.2f} 元"


def money(value: object) -> str:
    number = Decimal(str(value)).quantize(MONEY, rounding=ROUND_HALF_UP)
    return f"{number:,.2f} 元"


def signed_rate(value: object) -> str:
    number = Decimal(str(value)).quantize(RATE, rounding=ROUND_HALF_UP)
    return f"{number:+,.2f}%"


def trend_chart(item: dict, section_id: str, index: int, color: str) -> dict:
    raw = item.get("近15日利润走势") or []
    if len(raw) != 15:
        raise RuntimeError(f"enterprise alert trend must contain 15 days: {item.get('stable_key')}")
    profits = [Decimal(str(point["利润"])) for point in raw]
    low, high = min(profits), max(profits)
    values = [{
        "date": date.fromisoformat(point["日期"]).strftime("%m-%d"),
        "trendIndex": 50 if high == low else int((profit - low) / (high - low) * 100),
    } for point, profit in zip(raw, profits)]
    return {
        "tag": "chart", "element_id": f"{section_id}_trend_{index}",
        "height": "48px", "preview": False, "color_theme": "brand",
        "chart_spec": {
            "type": "line", "data": {"values": values},
            "xField": "date", "yField": "trendIndex", "padding": 0,
            "axes": [{"orient": "left", "visible": False},
                     {"orient": "bottom", "visible": False}],
            "legends": {"visible": False}, "tooltip": {"visible": False},
            "label": {"visible": False}, "point": {"visible": False},
            "color": [color], "line": {"style": {"lineWidth": 2}},
        },
    }


def alert_element(item: dict, section_id: str, index: int, color: str) -> dict:
    change = Decimal(str(item["利润变化额"]))
    direction = "下降" if change < 0 else "上升"
    content = (
        f"企业名称：**{item['企业名称']}**  <text_tag color='{color}'>{direction}</text_tag>\n"
        f"利润变化额：<font color='{color}'>**{signed_money(item['利润变化额'])}**</font>\n"
        f"利润变化率：<font color='{color}'>**{signed_rate(item['利润变化率'])}**</font>\n"
        f"昨日利润：{money(item['昨日利润'])}\n"
        f"对比利润：{money(item['对比利润'])}\n"
        f"近15日利润走势（{item['近15日利润走势'][0]['日期'][5:]}—"
        f"{item['近15日利润走势'][-1]['日期'][5:]}）"
    )
    return {
        "tag": "column_set", "element_id": f"{section_id}_alert_{index}", "flex_mode": "stretch",
        "columns": [{
            "tag": "column", "width": "weighted", "weight": 1,
            "background_style": "default", "padding": "12px",
            "elements": [{"tag": "markdown", "content": content},
                         trend_chart(item, section_id, index, color)],
        }],
    }


def calendar_alert_element(item: dict, index: int) -> dict:
    reason = ("利润由正转负" if item["status"] == "ALERT_SIGN_FLIP"
              else "近3日内至少2日低于各自正常基线")
    content = (
        f"企业名称：**{item['企业名称']}**  <text_tag color='red'>{reason}</text_tag>\n"
        f"业务类型：{item['business_type']}\n"
        f"实际利润：{money(item['实际利润'])}\n"
        f"基线利润：{money(item['基线利润'])}\n"
        f"利润偏差额：<font color='red'>**{signed_money(item['利润偏差额'])}**</font>\n"
        f"利润偏差率：<font color='red'>**{signed_rate(item['利润偏差率'])}**</font>\n"
        f"正常波动区间：{money(item['正常波动下限'])} — {money(item['正常波动上限'])}\n"
        f"基线样本：{item['baseline_sample_count']} 个同星期样本\n"
        f"近15日利润走势（{item['近15日利润走势'][0]['日期'][5:]}—"
        f"{item['近15日利润走势'][-1]['日期'][5:]}）"
    )
    return {
        "tag": "column_set", "element_id": f"profit_alert_{index}", "flex_mode": "stretch",
        "columns": [{
            "tag": "column", "width": "weighted", "weight": 1,
            "background_style": "default", "padding": "12px",
            "elements": [{"tag": "markdown", "content": content},
                         trend_chart(item, "profit_alert", index, "red")],
        }],
    }


def build_calendar_card(alert: dict, rules: dict) -> dict:
    current = date.fromisoformat(alert["current_day"])
    body = [{
        "tag": "markdown",
        "content": (
            f"业务告警 **{alert['alert_count']} 家**｜单日下降观察 {alert['watch_count']} 家｜"
            f"利润上升观察 {alert['observation_count']} 家｜暂不判定 {alert['unrated_count']} 家\n"
            f"数据日：{current.isoformat()}\n"
            f"规则：最近 6–8 个同星期样本中位数为基线，超过 **3×MAD**，且偏差额绝对值 "
            f"**> {rules['thresholds']['amount_exclusive']}元**、偏差率绝对值 "
            f"**> {rules['thresholds']['rate_percent_exclusive']}%**"
        ),
    }]
    if alert["alerts"]:
        body.extend([{"tag": "hr"}, {"tag": "markdown", "element_id": "profit_alert_heading",
                     "content": f"## <font color='red'>需要处理 · {alert['alert_count']} 家</font>"}])
        body.extend(calendar_alert_element(item, index)
                    for index, item in enumerate(alert["alerts"], start=1))
    else:
        body.append({"tag": "markdown", "content": "本次没有满足新规则的业务告警。"})
    return {
        "schema": "2.0",
        "config": {"update_multi": True, "width_mode": "fill", "enable_forward": False,
                   "summary": {"content": f"企业客户利润异常告警｜{current.isoformat()}"}},
        "header": {
            "title": {"tag": "plain_text", "content": f"企业客户利润异常告警 · {current.month}月{current.day}日"},
            "subtitle": {"tag": "plain_text", "content": "企业自身同星期稳健基线"},
            "template": "red", "icon": {"tag": "standard_icon", "token": "warning_colorful"},
        },
        "body": {"direction": "vertical", "padding": "12px 12px 20px 12px",
                 "vertical_spacing": "8px", "elements": body},
    }


def build(alert: dict, rules: dict) -> dict:
    if alert.get("status") != "SUCCESS" or alert.get("ruleset_version") != rules.get("version"):
        raise RuntimeError("alert artifact is not valid for the active alert rules")
    if alert.get("alert_count") != len(alert.get("alerts") or []):
        raise RuntimeError("alert count mismatch")
    if alert.get("schema_version") == "2.0":
        return build_calendar_card(alert, rules)
    current = date.fromisoformat(alert["current_day"])
    comparison = date.fromisoformat(alert["comparison_day"])
    grouped = {
        "profit_decrease": [item for item in alert["alerts"] if Decimal(str(item["利润变化额"])) < 0],
        "profit_increase": [item for item in alert["alerts"] if Decimal(str(item["利润变化额"])) > 0],
    }
    body = [{
        "tag": "markdown",
        "content": (
            f"共发现 **{alert['alert_count']} 家**企业利润异常｜"
            f"<font color='red'>下降 {len(grouped['profit_decrease'])} 家</font>｜"
            f"<font color='green'>上升 {len(grouped['profit_increase'])} 家</font>\n"
            f"比较：{current.isoformat()} vs {comparison.isoformat()}\n"
            "规则：利润变化额绝对值 **> 200元** 且利润变化率绝对值 **> 10%**"
        ),
    }]
    for section in rules["display"]["sections"]:
        section_id = section["id"]
        items = grouped[section_id]
        color = section["color"]
        body.append({"tag": "hr"})
        body.append({"tag": "markdown", "element_id": f"{section_id}_heading",
                     "content": f"## <font color='{color}'>{section['title']} · {len(items)} 家</font>"})
        if not items:
            body.append({"tag": "markdown", "content": f"本次无{section['title']}企业。"})
        for index, item in enumerate(items, start=1):
            body.append(alert_element(item, section_id, index, color))
    if not alert.get("alerts"):
        body.append({"tag": "markdown", "content": "未发现满足双阈值的企业利润异常。"})
    return {
        "schema": "2.0",
        "config": {
            "update_multi": True, "width_mode": "fill", "enable_forward": False,
            "summary": {"content": f"企业客户利润异常告警｜{current.isoformat()}"},
        },
        "header": {
            "title": {"tag": "plain_text", "content": f"企业客户利润异常告警 · {current.month}月{current.day}日"},
            "subtitle": {"tag": "plain_text", "content": f"昨日 {current.isoformat()}｜对比 {comparison.isoformat()}"},
            "template": "red",
            "icon": {"tag": "standard_icon", "token": "warning_colorful"},
        },
        "body": {
            "direction": "vertical", "padding": "12px 12px 20px 12px",
            "vertical_spacing": "8px", "elements": body,
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--alert", type=Path, required=True)
    parser.add_argument("--rules", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    card = build(read(args.alert), read(args.rules))
    args.output.write_text(json.dumps(card, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "SUCCESS", "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
