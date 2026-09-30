"""Build an Aetherfall login notice and server-list override for the 4.8 NA client."""

import argparse
import copy
import io
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


NOTICE_ENTRY = "ui/loginnotice.xml"
SERVER_LIST_ENTRY = "ui/serverlist.xml"
SERVER_ID = "1"
SERVER_NAME = "Aetherfall"
NOTICE_HTML = (
    '<html><body><font color="ffffff">'
    '<font color="66FFFF">Aetherfall - New in Atreia</font><BR><BR>'
    '<font color="66FFFF">Black Cloud Marketplace</font><BR>'
    'Spend Kinah on items. Purchases arrive by mail.<BR><BR>'
    '<font color="66FFFF">Central Market</font><BR>'
    'Search, list, and buy tradeable items from other players.<BR><BR>'
    '<font color="66FFFF">Inventory Update</font><BR>'
    'A wider 12-column cube with item search and more room.<BR><BR>'
    '<font color="66FFFF">Quick Access</font><BR>'
    'Open Transmog, Broker, and Central Market from the in-game menu.'
    '</font></body></html>'
)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--codec-directory", type=Path, required=True)
    parser.add_argument("--client-pak", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    sys.path.insert(0, str(args.codec_directory.resolve()))
    from fire_temple_probe import binary_xml, read_pak
    from patch_client_world import encode_binary_xml, encode_pak

    if args.output.resolve() == args.client_pak.resolve():
        raise ValueError("Output must be staged outside the client")

    with read_pak(args.client_pak) as source:
        names = source.namelist()
        if names.count(NOTICE_ENTRY) != 1 or names.count(SERVER_LIST_ENTRY) != 1:
            raise ValueError("Expected one notice and one server list entry")

        notice = binary_xml(source.read(NOTICE_ENTRY))
        content = notice.find("Contents")
        if content is None:
            raise ValueError("Login notice has no Contents element")
        content.text = NOTICE_HTML

        servers = binary_xml(source.read(SERVER_LIST_ENTRY))
        matches = [server for server in servers.findall("server") if server.findtext("id") == SERVER_ID]
        if len(matches) != 1 or matches[0].find("name") is None:
            raise ValueError("Server ID 1 is missing or duplicated")
        matches[0].find("name").text = SERVER_NAME

        replacements = {
            NOTICE_ENTRY: encode_binary_xml(notice),
            SERVER_LIST_ENTRY: encode_binary_xml(servers),
        }
        for name, expected in [(NOTICE_ENTRY, notice), (SERVER_LIST_ENTRY, servers)]:
            actual = binary_xml(replacements[name])
            if ET.tostring(actual) != ET.tostring(expected):
                raise ValueError(f"Binary XML round-trip failed: {name}")

        plain_pak = io.BytesIO()
        with zipfile.ZipFile(plain_pak, "w") as target:
            for info in source.infolist():
                target.writestr(copy.copy(info), replacements.get(info.filename, source.read(info.filename)))

        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(encode_pak(plain_pak.getvalue()))

        with read_pak(args.output) as check:
            if check.testzip() is not None or check.namelist() != names:
                raise ValueError("Staged archive failed CRC or entry-order validation")
            for name in names:
                expected = replacements.get(name)
                if expected is None:
                    expected = source.read(name)
                if check.read(name) != expected:
                    raise ValueError(f"Staged archive changed an unexpected entry: {name}")

    print(f"Staged {args.output} ({len(names)} entries; two XML entries changed)")


if __name__ == "__main__":
    main()
