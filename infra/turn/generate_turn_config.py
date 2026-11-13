#!/usr/bin/env python3
"""Generate a private coturn configuration. Does not install, start, or provision anything."""
import argparse
import ipaddress
import os
from pathlib import Path
import re
import secrets

DENIED = (
    "0.0.0.0-0.255.255.255",
    "10.0.0.0-10.255.255.255",
    "100.64.0.0-100.127.255.255",
    "127.0.0.0-127.255.255.255",
    "169.254.0.0-169.254.255.255",
    "172.16.0.0-172.31.255.255",
    "192.168.0.0-192.168.255.255",
    "198.18.0.0-198.19.255.255",
    "224.0.0.0-255.255.255.255",
    "::1",
    "fc00::-fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
    "fe80::-febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
)
def dns_name(value):
    if len(value)>253 or not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?",value):
        raise ValueError("Invalid relay hostname")
    labels=value.split(".")
    if len(labels)<2 or any(not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?",label) for label in labels):
        raise ValueError("A public DNS hostname is required")
    return value.lower()

def safe_path(value):
    p=Path(value)
    if not p.is_absolute() or not re.fullmatch(r"/[A-Za-z0-9_./-]+",str(p)):
        raise ValueError("Certificate paths must be absolute and contain no whitespace")
    return str(p)

def build_config(host, local, external, cert, key, secret):
    host=dns_name(host)
    local=ipaddress.IPv4Address(local)
    external=ipaddress.IPv4Address(external)
    if not external.is_global or local.is_loopback or local.is_unspecified or local.is_multicast or local.is_link_local:
        raise ValueError("Public external IPv4 and routable local interface required")
    if not re.fullmatch(r"[a-f0-9]{64}",secret):
        raise ValueError("Expected 256-bit hexadecimal secret")
    lines=[
        "listening-port=3478","tls-listening-port=5349",
        f"listening-ip={local}",f"relay-ip={local}",f"external-ip={external}/{local}",
        "min-port=49160","max-port=49200",f"realm={host}",
        "fingerprint","use-auth-secret",f"static-auth-secret={secret}",
        f"cert={safe_path(cert)}",f"pkey={safe_path(key)}",
        "no-tlsv1","no-tlsv1_1","no-cli","no-multicast-peers",
        "user-quota=4","total-quota=32","max-bps=64000","bps-capacity=2048000",
        "stale-nonce=600","log-file=syslog","no-stdout-log",
    ]
    lines.extend("denied-peer-ip="+network for network in DENIED)
    return "\n".join(lines)+"\n"

def write_private(path, content):
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
    try:
        with os.fdopen(fd,"w",encoding="utf-8") as out:
            out.write(content)
            out.flush()
            os.fsync(out.fileno())
    except BaseException:
        Path(path).unlink(missing_ok=True)
        raise

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hostname",required=True)
    parser.add_argument("--local-ip",required=True)
    parser.add_argument("--public-ip",required=True)
    parser.add_argument("--cert",required=True)
    parser.add_argument("--key",required=True)
    parser.add_argument("--output",required=True)
    args=parser.parse_args()
    try:
        config=build_config(args.hostname,args.local_ip,args.public_ip,args.cert,args.key,secrets.token_hex(32))
        write_private(args.output,config)
    except (ValueError,OSError) as e:
        parser.error(str(e))
    print("Created private configuration; existing files are never overwritten.")
    print("Configure coturn using the generated file and a trusted certificate.")
    print("Do not print, commit or transmit the configuration or shared secret in chat.")
if __name__=="__main__":
    main()
