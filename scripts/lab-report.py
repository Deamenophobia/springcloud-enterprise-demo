#!/usr/bin/env python3
"""Summarize a clean framework-labs run without exporting JVM properties or logs."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LAB = ROOT / "framework-labs"


def read_suite(path):
    suite = ET.parse(path).getroot()
    cases = []
    for case in suite.findall("testcase"):
        status = "PASS"
        for tag, label in (("skipped", "SKIP"), ("failure", "FAIL"), ("error", "ERROR")):
            if case.find(tag) is not None:
                status = label
        cases.append({"name": case.attrib["name"], "status": status})
    if not cases or int(suite.attrib["tests"]) != len(cases):
        raise ValueError("empty or inconsistent test count")
    for attribute, status in (("failures", "FAIL"), ("errors", "ERROR"), ("skipped", "SKIP")):
        if int(suite.attrib.get(attribute, "0")) != sum(case["status"] == status for case in cases):
            raise ValueError("inconsistent " + attribute + " count")
    return {"name": suite.attrib["name"], "cases": sorted(cases, key=lambda case: case["name"])}


def collect(lab):
    suites, problems = [], []
    source_root = lab / "src" / "test" / "java"
    # These lessons use one top-level JUnit class per *Test.java file.
    sources = sorted(source_root.rglob("*Test.java"))
    if not sources:
        problems.append("No laboratory test sources found")
    for source in sources:
        name = ".".join(source.relative_to(source_root).with_suffix("").parts)
        path = lab / "target" / "surefire-reports" / ("TEST-" + name + ".xml")
        try:
            suite = read_suite(path)
            if suite["name"] != name:
                raise ValueError("suite name does not match source")
            suites.append(suite)
        except (OSError, ET.ParseError, ValueError, KeyError):
            problems.append("Missing or invalid report: " + name)
    counts = {status: sum(case["status"] == status for suite in suites for case in suite["cases"])
              for status in ("PASS", "FAIL", "ERROR", "SKIP")}
    successful = not problems and counts["PASS"] > 0 and not any(counts[s] for s in ("FAIL", "ERROR", "SKIP"))
    return {"successful": successful, "counts": counts, "problems": problems, "suites": suites}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def markdown(report):
    counts = report["counts"]
    lines = ["# Spring Framework laboratory results", "",
             "Status: **" + ("PASS" if report["successful"] else "INCOMPLETE OR FAILED") + "**", "",
             "- Report generated (UTC): " + report["generated_utc"],
             "- Source HEAD: `" + report["commit"] + "`",
             "- Working tree: " + ("modified; HEAD alone does not identify the tested sources" if report["dirty"] else "clean"),
             "- Passed: {PASS}; failures: {FAIL}; errors: {ERROR}; skipped: {SKIP}.".format(**counts), "",
             "Command: `mvn -B -ntp -pl framework-labs clean test` (or the full clean reactor build).", "",
             "Run the command immediately before generating this report. This tool reads XML; it does not run Maven or verify report freshness.", "",
             "Scope: container behavior, Boot JDBC auto-configuration, and transaction proxies with H2. "
             "This is not PostgreSQL, Docker, Kubernetes, load, or production-readiness evidence.", ""]
    lines.extend("- " + problem for problem in report["problems"])
    for suite in report["suites"]:
        lines.extend(["", "## " + suite["name"].rsplit(".", 1)[-1], "", "| Test | Result |", "|---|---|"])
        for case in suite["cases"]:
            name = case["name"].replace("|", "\\|").replace("\n", " ")
            lines.append("| `" + name + "` | " + case["status"] + " |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    report = collect(LAB)
    report.update(generated_utc=datetime.now(timezone.utc).isoformat(),
                  commit=git("rev-parse", "HEAD"), dirty=bool(git("status", "--porcelain")))
    output = LAB / "target" / "evidence"
    output.mkdir(parents=True, exist_ok=True)
    (output / "results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (output / "results.md").write_text(markdown(report), encoding="utf-8")
    print("Laboratory evidence: framework-labs/target/evidence/results.md")
    print(json.dumps(report["counts"]))
    return 0 if report["successful"] else 1


if __name__ == "__main__":
    sys.exit(main())
