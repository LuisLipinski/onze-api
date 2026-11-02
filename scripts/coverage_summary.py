"""Summarize JaCoCo's root counters without double-counting packages/classes."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET


def counters(element):
    result = {}
    for counter in element.findall("counter"):
        missed, covered = int(counter.attrib["missed"]), int(counter.attrib["covered"])
        total = missed + covered
        result[counter.attrib["type"].lower()] = {
            "covered": covered, "missed": missed, "total": total,
            "pct": round(100 * covered / total, 2) if total else None,
        }
    return result


def summarize(path):
    root = ET.parse(path).getroot()
    metrics = counters(root)
    if any(metrics.get(key, {}).get("total", 0) == 0 for key in ("line", "branch")):
        raise ValueError("JaCoCo report has no executable lines/branches; coverage was not measured")
    if metrics.get("instruction", {}).get("covered", 0) == 0:
        raise ValueError("JaCoCo recorded no executed instructions")
    files = []
    for package in root.findall("package"):
        for source in package.findall("sourcefile"):
            files.append({"path": f"{package.attrib['name']}/{source.attrib['name']}",
                          "metrics": counters(source)})
    return {"schemaVersion": 1, "tool": "JaCoCo 0.8.16", "metrics": metrics,
            "files": sorted(files, key=lambda item: item["path"])}


def percentage(metric):
    return "N/A" if metric.get("pct") is None else f"{metric['pct']:.2f}%"


def markdown(report):
    lines = ["# API: cobertura de codigo", "",
             "Suite: JUnit (unidade e integracao interna com PostgreSQL).",
             f"Testes: `{report.get('testStatus', 'unknown')}`. Revisao: `{report.get('revision', 'unknown')}`.",
             "", "| Metrica | Cobertura | Cobertos / total |", "|---|---:|---:|"]
    for key, label in (("line", "Linhas"), ("branch", "Ramificacoes (condicoes)"),
                       ("method", "Metodos"), ("instruction", "Instrucoes")):
        metric = report["metrics"].get(key, {})
        lines.append(f"| {label} | {percentage(metric)} | {metric.get('covered', 0)} / {metric.get('total', 0)} |")
    lines += ["", "Todos os arquivos Java de producao compilados participam, inclusive os nao executados.",
              "Branches medem os caminhos de if/switch; nao sao MC/DC nem todas as combinacoes booleanas.",
              "Os testes HTTP/Android externos do onze-automation nao estao incluidos nesta medicao.",
              "", "## Arquivos com mais ramificacoes nao cobertas", "",
              "| Arquivo | Linhas | Ramificacoes | Nao cobertas |", "|---|---:|---:|---:|"]
    ranked = sorted(report["files"], key=lambda row: row["metrics"].get("branch", {}).get("missed", 0), reverse=True)
    for row in ranked[:15]:
        metrics = row["metrics"]
        lines.append(f"| `{row['path']}` | {percentage(metrics.get('line', {}))} | {percentage(metrics.get('branch', {}))} | {metrics.get('branch', {}).get('missed', 0)} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xml", type=Path, default=Path("target/site/jacoco/jacoco.xml"))
    parser.add_argument("--test-status", default="unknown")
    args = parser.parse_args()
    report = summarize(args.xml)
    report["testStatus"] = args.test_status
    report["revision"] = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    output = args.xml.parent
    (output / "coverage-summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    summary = markdown(report)
    (output / "summary.md").write_text(summary, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as destination:
            destination.write(summary)
    print(summary)
