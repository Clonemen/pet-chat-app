#!/usr/bin/env python3
"""压缩 3D 生成服务产出的 GLB：把内嵌的大尺寸 PNG 贴图转成小尺寸 JPEG。

生成服务默认输出 4K PBR 贴图（单只模型约 40MB），直接放进 App 会让 APK 膨胀、
WebView 加载缓慢。本脚本只重编码贴图，几何/材质/动画数据原样保留。

用法：
    python3 tools/optimize_glb.py input.glb output.glb [--size 1024] [--quality 82]

依赖：Pillow
"""
import argparse
import io
import json
import struct
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("需要 Pillow：pip install Pillow")

TARGET_ARRAY_BUFFER = 34962
TARGET_ELEMENT_ARRAY_BUFFER = 34963


def read_glb(path):
    data = open(path, "rb").read()
    magic, version, total = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        raise ValueError("不是有效的 GLB v2 文件")
    offset = 12
    js = None
    bin_chunk = b""
    while offset < len(data):
        length, kind = struct.unpack_from("<I4s", data, offset)
        payload = data[offset + 8: offset + 8 + length]
        if kind == b"JSON":
            js = json.loads(payload.decode("utf-8").rstrip(" \x00"))
        elif kind.startswith(b"BIN"):
            bin_chunk = payload
        offset += 8 + length
    if js is None:
        raise ValueError("GLB 缺少 JSON chunk")
    return js, bin_chunk


def align4(n):
    return (n + 3) & ~3


def optimize(js, bin_chunk, size, quality):
    views = js.get("bufferViews", [])
    images = js.get("images", [])
    image_views = {}
    for idx, im in enumerate(images):
        if "bufferView" in im:
            image_views.setdefault(im["bufferView"], []).append(idx)

    # 抽出每个 bufferView 的原始字节
    raw = []
    for bv in views:
        start = bv.get("byteOffset", 0)
        end = start + bv["byteLength"]
        raw.append(bin_chunk[start:end])

    converted = 0
    for view_idx, img_indices in image_views.items():
        blob = raw[view_idx]
        try:
            img = Image.open(io.BytesIO(blob))
            img = img.convert("RGB")
            img.thumbnail((size, size), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=quality, optimize=True)
            new_blob = buf.getvalue()
        except Exception as exc:
            print(f"  ! 贴图 #{view_idx} 转换失败，保留原图: {exc}", file=sys.stderr)
            continue
        old_kb, new_kb = len(blob) / 1024, len(new_blob) / 1024
        print(f"  贴图 #{view_idx}: {old_kb:.0f}KB -> {new_kb:.0f}KB ({img.size[0]}x{img.size[1]} JPEG)")
        raw[view_idx] = new_blob
        for i in img_indices:
            images[i]["mimeType"] = "image/jpeg"
            if "name" in images[i]:
                images[i]["name"] = images[i]["name"].rsplit(".", 1)[0] + ".jpg"
        converted += 1

    # 重新拼接 BIN，保持 4 字节对齐并回填 offset/length
    out = bytearray()
    for i, bv in enumerate(views):
        while len(out) % 4:
            out.append(0)
        bv["byteOffset"] = len(out)
        bv["byteLength"] = len(raw[i])
        out.extend(raw[i])
    while len(out) % 4:
        out.append(0)

    buffers = js.get("buffers", [])
    if buffers:
        buffers[0]["byteLength"] = len(out)
    return js, bytes(out), converted


def write_glb(path, js, bin_chunk):
    js_bytes = json.dumps(js, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    js_bytes += b" " * (align4(len(js_bytes)) - len(js_bytes))
    bin_bytes = bin_chunk + b"\x00" * (align4(len(bin_chunk)) - len(bin_chunk))
    total = 12 + 8 + len(js_bytes) + 8 + len(bin_bytes)
    with open(path, "wb") as f:
        f.write(struct.pack("<4sII", b"glTF", 2, total))
        f.write(struct.pack("<I4s", len(js_bytes), b"JSON"))
        f.write(js_bytes)
        f.write(struct.pack("<I4s", len(bin_bytes), b"BIN\x00"))
        f.write(bin_bytes)
    return total


def main():
    ap = argparse.ArgumentParser(description="压缩 GLB 内嵌贴图")
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--size", type=int, default=1024, help="贴图最长边像素（默认 1024）")
    ap.add_argument("--quality", type=int, default=82, help="JPEG 质量（默认 82）")
    args = ap.parse_args()

    js, bin_chunk = read_glb(args.input)
    print(f"输入: {args.input} ({len(bin_chunk) / 1024 / 1024:.1f}MB BIN)")
    js, bin_chunk, n = optimize(js, bin_chunk, args.size, args.quality)
    total = write_glb(args.output, js, bin_chunk)
    print(f"输出: {args.output} ({total / 1024 / 1024:.1f}MB), 转换贴图 {n} 张")


if __name__ == "__main__":
    main()
