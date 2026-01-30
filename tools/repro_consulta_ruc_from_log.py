#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import re
import sys
import json
import shutil
import hashlib
import subprocess
from pathlib import Path
from datetime import datetime
from typing import Optional, List, Tuple, Dict, Any

def sha256(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()

def hexdump_prefix(b: bytes, n: int = 96) -> str:
    import binascii
    x = b[:n]
    hx = binascii.hexlify(x).decode("ascii")
    return " ".join(hx[i:i+2] for i in range(0, len(hx), 2))

def read_bytes(p: Path) -> bytes:
    return p.read_bytes()

def extract_first_xml_line_from_log(log_bytes: bytes) -> Optional[bytes]:
    # Busca la primera "línea" (bytes) que contenga <env:Envelope ... </env:Envelope>
    for line in log_bytes.splitlines(keepends=False):
        if b"<env:Envelope" in line and b"</env:Envelope>" in line:
            start = line.find(b"<?xml")
            if start == -1:
                start = line.find(b"<env:Envelope")
            if start != -1:
                return line[start:]
            return line
    return None

def variant_remove_standalone(xml: bytes) -> bytes:
    if not xml.lstrip().startswith(b"<?xml"):
        return xml
    m = re.search(br"^\s*<\?xml[^?]*\?>", xml, flags=re.DOTALL)
    if not m:
        return xml
    decl = m.group(0)
    new_decl = re.sub(br"\s+standalone=(\"no\"|'no')", b"", decl)
    return new_decl + xml[m.end():]

def variant_remove_xml_decl(xml: bytes) -> bytes:
    s = xml.lstrip()
    if not s.startswith(b"<?xml"):
        return xml
    m = re.search(br"^\s*<\?xml[^?]*\?>", xml, flags=re.DOTALL)
    if not m:
        return xml
    return xml[m.end():]

def variant_crlf(xml: bytes) -> bytes:
    if b"\n" not in xml:
        return xml
    xml = xml.replace(b"\r\n", b"\n")
    return xml.replace(b"\n", b"\r\n")

def variant_minify_intertag_ws(xml: bytes) -> bytes:
    return re.sub(br">\s+<", b"><", xml)

def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)

def run_curl(variant_name: str, body_path: Path, out_dir: Path) -> Dict[str, Any]:
    URL = "https://sifen-test.set.gov.py/de/ws/consultas/consulta-ruc.wsdl"
    P12_PATH = "/Users/robinklaiss/.sifen/certs/F1T_65478.p12"
    P12_PASS = os.environ.get("P12_PASS", "")
    if not P12_PASS:
        raise SystemExit('Falta P12_PASS. Exportá:  export P12_PASS="TU_PASSWORD_P12"')

    ensure_dir(out_dir)
    shutil.copy2(body_path, out_dir / "req.xml")

    headers_path = out_dir / "headers.txt"
    resp_path = out_dir / "response.xml"
    trace_path = out_dir / "trace.txt"

    cmd = [
        "curl", "-sS", "--http1.1", "-k",
        "--cert-type", "P12",
        "--cert", "{}:{}".format(P12_PATH, P12_PASS),
        "-H", "User-Agent: rshk-jsifenlib/0.2.4 (LVEA)",
        "-H", "Content-Type: application/xml; charset=utf-8",
        "-H", "Accept: text/html, image/gif, image/jpeg, */*; q=0.2",
        "-H", "Connection: keep-alive",
        "-D", str(headers_path),
        "-o", str(resp_path),
        "--trace-ascii", str(trace_path),
        "--data-binary", "@{}".format(body_path),
        URL,
    ]

    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    resp_bytes = resp_path.read_bytes() if resp_path.exists() else b""
    hdr_first = ""
    if headers_path.exists():
        lines = headers_path.read_text(errors="replace").splitlines()
        hdr_first = lines[0] if lines else ""

    ok = (b"dCodRes>0502<" in resp_bytes) and (b"rResEnviConsRUC" in resp_bytes)

    cod = None
    msg = None
    m_cod = re.search(br"<[^:>]*:?dCodRes>([^<]+)</", resp_bytes)
    m_msg = re.search(br"<[^:>]*:?dMsgRes>([^<]+)</", resp_bytes)
    if m_cod:
        cod = m_cod.group(1).decode("utf-8", "replace")
    if m_msg:
        msg = m_msg.group(1).decode("utf-8", "replace")

    return {
        "variant": variant_name,
        "http_first_line": hdr_first,
        "curl_rc": p.returncode,
        "stderr_tail": p.stderr.decode("utf-8", "replace")[-300:],
        "dCodRes": cod,
        "dMsgRes": msg,
        "ok_0502": ok,
        "req_bytes": body_path.stat().st_size if body_path.exists() else None,
        "req_sha256": sha256(body_path.read_bytes()) if body_path.exists() else None,
    }

def main() -> None:
    run_ok = Path("artifacts/run_20260130_030545")
    if len(sys.argv) >= 2:
        run_ok = Path(sys.argv[1])

    log_path = run_ok / "runner.log"
    req_saved_path = run_ok / "consulta_ruc_request.xml"

    if not log_path.exists():
        raise SystemExit("No existe: {}".format(log_path))
    if not req_saved_path.exists():
        raise SystemExit("No existe: {}".format(req_saved_path))

    log_bytes = read_bytes(log_path)
    xml_line = extract_first_xml_line_from_log(log_bytes)
    if not xml_line:
        raise SystemExit("No encontré ninguna línea con <env:Envelope ... </env:Envelope> en runner.log")

    saved_bytes = read_bytes(req_saved_path)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    base_out = Path("artifacts/curl_matrix") / "run_{}_repro".format(ts)
    ensure_dir(base_out)

    req_from_log_path = base_out / "req_from_log.xml"
    req_from_log_path.write_bytes(xml_line)

    print("== INPUTS ==")
    print("RUN_OK:", run_ok)
    print("runner.log:", log_path)
    print("saved_req:", req_saved_path)
    print("")
    print("== BYTES / SHA256 ==")
    print("req_from_log.xml  bytes={} sha256={}".format(len(xml_line), sha256(xml_line)))
    print("consulta_ruc_request.xml bytes={} sha256={}".format(len(saved_bytes), sha256(saved_bytes)))
    print("")
    print("== HEXDUMP (prefix 96 bytes) ==")
    print("from_log:", hexdump_prefix(xml_line))
    print("saved   :", hexdump_prefix(saved_bytes))
    print("")

    variants = []  # type: List[Tuple[str, bytes]]
    variants.append(("v0_from_log_exact", xml_line))
    variants.append(("v1_remove_standalone", variant_remove_standalone(xml_line)))
    variants.append(("v2_remove_xml_decl", variant_remove_xml_decl(xml_line)))
    variants.append(("v3_crlf", variant_crlf(xml_line)))
    variants.append(("v4_minify_intertag_ws", variant_minify_intertag_ws(xml_line)))

    uniq = []  # type: List[Tuple[str, bytes]]
    seen = set()
    for name, b in variants:
        h = sha256(b)
        if h in seen:
            continue
        seen.add(h)
        uniq.append((name, b))

    var_paths = []  # type: List[Tuple[str, Path, bytes]]
    for name, b in uniq:
        p = base_out / "{}.xml".format(name)
        p.write_bytes(b)
        var_paths.append((name, p, b))

    print("== VARIANTS WRITTEN ==")
    for name, p, b in var_paths:
        print("- {}: {} bytes={} sha256={}".format(name, p, len(b), sha256(b)))
    print("")

    results = []  # type: List[Dict[str, Any]]
    for name, p, b in var_paths:
        out_dir = base_out / name
        try:
            r = run_curl(name, p, out_dir)
        except Exception as e:
            r = {"variant": name, "error": str(e), "ok_0502": False}
        results.append(r)

    (base_out / "summary.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")

    print("== RESULTS ==")
    best = None
    for r in results:
        v = r.get("variant")
        ok = r.get("ok_0502")
        http = r.get("http_first_line")
        cod = r.get("dCodRes")
        msg = r.get("dMsgRes")
        print("- {}: ok_0502={} http='{}' dCodRes={} dMsgRes={}".format(v, ok, http, cod, msg))
        if ok and best is None:
            best = r

    print("")
    print("== OUTPUT DIR ==")
    print(str(base_out))
    print("")

    if best:
        print("✅ FOUND OK VARIANT:")
        print(json.dumps(best, indent=2, ensure_ascii=False))
        sys.exit(0)

    print("❌ No hubo 0502. Diff corto (texto) req_from_log vs saved request…")
    try:
        a = xml_line.decode("utf-8", "replace")
        b = saved_bytes.decode("utf-8", "replace")
        import difflib
        d = difflib.unified_diff(
            a.splitlines(), b.splitlines(),
            fromfile="req_from_log", tofile="consulta_ruc_request",
            lineterm=""
        )
        diff_txt = "\n".join(list(d)[:80])
        (base_out / "diff_short.txt").write_text(diff_txt, encoding="utf-8")
        print(diff_txt if diff_txt else "(sin diff visible; puede ser diferencia de bytes invisibles)")
        print("\n(diff_short.txt guardado en {})".format(base_out / "diff_short.txt"))
    except Exception as e:
        print("(No pude generar diff: {})".format(e))

    sys.exit(1)

if __name__ == "__main__":
    main()
