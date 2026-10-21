"""Inspect built APK structure without an Android SDK (signature crypto uses CI apksigner)."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile
import zlib


def strings_at(data, offset):
    _, header, _ = struct.unpack_from('<HHI', data, offset)
    count, _, flags, start, _ = struct.unpack_from('<IIIII', data, offset + 8)
    result = []
    for index in range(count):
        at = offset + start + struct.unpack_from('<I', data, offset + header + index * 4)[0]
        if flags & 0x100:
            def length(pos):
                value = data[pos]
                return (((value & 127) << 8) | data[pos + 1], pos + 2) if value & 128 else (value, pos + 1)
            _, at = length(at)
            size, at = length(at)
            result.append(data[at:at + size].decode('utf-8'))
        else:
            size = struct.unpack_from('<H', data, at)[0]
            at += 2
            if size & 0x8000:
                size = ((size & 0x7fff) << 16) | struct.unpack_from('<H', data, at)[0]
                at += 2
            result.append(data[at:at + size * 2].decode('utf-16-le'))
    return result


def manifest(data):
    pool, tags, at = [], [], 8
    while at < len(data):
        kind, header, size = struct.unpack_from('<HHI', data, at)
        assert size >= header and size > 0
        if kind == 1:
            pool = strings_at(data, at)
        elif kind == 0x102:
            name = pool[struct.unpack_from('<I', data, at + 20)[0]]
            start, stride, count = struct.unpack_from('<HHH', data, at + 24)
            attrs = {}
            for index in range(count):
                pos = at + 16 + start + index * stride
                key, raw = struct.unpack_from('<II', data, pos + 4)
                value_type, value = data[pos + 15], struct.unpack_from('<I', data, pos + 16)[0]
                attrs[pool[key]] = pool[value] if value_type == 3 else pool[raw] if raw != 0xffffffff else value
            tags.append((name, attrs))
        at += size
    return tags


apk = Path(sys.argv[1])
data = apk.read_bytes()
if len(sys.argv) > 2:
    assert hashlib.sha256(data).hexdigest() == sys.argv[2], 'APK checksum differs from CI'
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None, 'ZIP CRC mismatch'
    assert {'AndroidManifest.xml', 'classes.dex', 'resources.arsc'} <= set(archive.namelist())
    dex = archive.read('classes.dex')
    assert dex.startswith(b'dex\n')
    assert hashlib.sha1(dex[32:]).digest() == dex[12:32]
    assert zlib.adler32(dex[12:]) == struct.unpack_from('<I', dex, 8)[0]
    tags = manifest(archive.read('AndroidManifest.xml'))
    app = next(attrs for name, attrs in tags if name == 'manifest')
    sdk = next(attrs for name, attrs in tags if name == 'uses-sdk')
    application = next(attrs for name, attrs in tags if name == 'application')
    assert app['package'] == 'com.stemnote.app'
    assert sdk['minSdkVersion'] == 26 and sdk['targetSdkVersion'] == 35
    assert application['debuggable'] != 0
    permissions = [attrs['name'] for name, attrs in tags if name == 'uses-permission']
    assert 'android.permission.RECORD_AUDIO' in permissions
    assert 'android.permission.INTERNET' not in permissions
eocd = data.rfind(b'PK\x05\x06')
central = struct.unpack_from('<I', data, eocd + 16)[0]
assert data[central - 16:central] == b'APK Sig Block 42', 'APK signing block missing'
print(json.dumps({'file': str(apk), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'package': app['package'], 'version': app['versionName'], 'minSdk': sdk['minSdkVersion'], 'targetSdk': sdk['targetSdkVersion'], 'debug': True, 'permissions': permissions, 'zip_crc': 'pass', 'dex_checksums': 'pass', 'signing_block': 'present'}, ensure_ascii=False, indent=2))
