# -*- coding: utf-8 -*-
"""
玻璃质感调档对比渲染器
用真实水纹背景 + 新旧两套参数，渲染「面板 / 主按钮 / 文字按钮 / 图标按钮」对比图。
（纯 PIL 模拟 Compose 的 glassSheen + 渐变 + 描边 + 投影数学，用于确认调档方向）
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

W, H = 1080, 1560
DP = 3.0  # 设备 density=3，1dp = 3px

BG_TOP = (234, 247, 255)
BG_MID = (210, 238, 255)
BG_BOT = (155, 214, 255)
PRIMARY = (30, 159, 216)
EDGE_DEEP = (7, 49, 76)
TEXT_DARK = (14, 46, 63)
GLASS_TINT = (220, 239, 251)

# ---------------- 旧参数（调档前） ----------------
OLD = dict(
    panel_top=0.90, panel_bot=0.68, panel_edge=0.92,
    shadow_amb=0.30, shadow_spot=0.42,
    gloss_a=0.55, gloss_mid=0.15, gloss_end=1.00, gloss_mid_at=0.34,
    shade_a=0.20, shade_from=0.58,
    spec_a=0.34, spec_r=0.75, spec_cx=0.30, spec_cy=0.02,
    btn_lighten=0.26, btn_darken=0.18, btn_edge=0.62, btn_r=16,
    tb_top=0.72, tb_bot=0.34, tb_edge=0.75,
    ib_top=0.85, ib_bot=0.42, ib_edge=0.80,
    out_top=0.80, out_bot=0.44,
)

# ---------------- 新参数（调档后：更通透 / 反光更聚拢 / 更圆润） ----------------
NEW = dict(
    panel_top=0.72, panel_bot=0.40, panel_edge=0.95,
    shadow_amb=0.26, shadow_spot=0.38,
    gloss_a=0.62, gloss_mid=0.12, gloss_end=0.62, gloss_mid_at=0.26,
    shade_a=0.16, shade_from=0.62,
    spec_a=0.42, spec_r=0.60, spec_cx=0.28, spec_cy=0.01,
    btn_lighten=0.30, btn_darken=0.16, btn_edge=0.70, btn_r=20,
    tb_top=0.52, tb_bot=0.18, tb_edge=0.78,
    ib_top=0.66, ib_bot=0.26, ib_edge=0.82,
    out_top=0.60, out_bot=0.26,
)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def make_bg(w, h):
    img = Image.new("RGB", (w, h), BG_TOP)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        if t < 0.34:
            c = lerp(BG_TOP, BG_MID, t / 0.34)
        elif t < 0.70:
            c = lerp(BG_MID, BG_BOT, (t - 0.34) / 0.36)
        else:
            c = lerp(BG_BOT, (140, 205, 252), (t - 0.70) / 0.30)
        d.line([(0, y), (w, y)], fill=c)
    # 水波纹装饰（与 App 的 WaterRipples 同款）
    ov = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    for cx, cy, mr, a in [(w * 0.16, h * 0.14, min(w, h) * 0.55, 0.28),
                           (w * 0.86, h * 0.90, min(w, h) * 0.60, 0.16)]:
        for k in range(3):
            rr = mr * (0.30 + k * 0.28)
            od.ellipse([cx - rr, cy - rr, cx + rr, cy + rr],
                       outline=(255, 255, 255, int(255 * a * (1 - k * 0.3))),
                       width=9)
    ov = ov.filter(ImageFilter.GaussianBlur(2))
    return Image.alpha_composite(img.convert("RGBA"), ov)


def vgrad(size, stops):
    """stops: list of (pos 0-1, (r,g,b,a))"""
    w, h = size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        # 找区间
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                k = 0 if p1 == p0 else (t - p0) / (p1 - p0)
                col = tuple(int(round(c0[j] + (c1[j] - c0[j]) * k)) for j in range(4))
                break
        else:
            col = stops[-1][1]
        d.line([(0, y), (w, y)], fill=col)
    return img


def radial(size, center, radius, alpha):
    w, h = size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = center
    steps = 90
    for i in range(steps, 0, -1):
        r = radius * i / steps
        a = int(round(255 * alpha * (1 - i / steps)))
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, a))
    return img.filter(ImageFilter.GaussianBlur(radius * 0.08))


def glass_shape(base_img, box, radius, P, is_button=False, tint=None,
                top_a=None, bot_a=None, edge_a=None, btn_edge_a=None,
                btn_grad=None):
    """在 base_img 上画一个玻璃元件（面板或按钮）"""
    x0, y0, x1, y1 = box
    w, h = int(x1 - x0), int(y1 - y0)
    if w <= 0 or h <= 0:
        return base_img

    # 1) 投影
    sh = Image.new("RGBA", base_img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    if is_button and btn_grad:
        c = btn_grad[0]
    else:
        c = PRIMARY
    sd.rounded_rectangle([x0, y0 + int(9 * DP), x1, y1 + int(9 * DP)],
                         radius=radius, fill=(c[0], c[1], c[2], int(255 * P['shadow_spot'])))
    sh = sh.filter(ImageFilter.GaussianBlur(9 * DP))
    base_img = Image.alpha_composite(base_img, sh)

    # 2) 形状遮罩
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius=radius, fill=255)

    # 3) 玻璃底色
    if is_button and btn_grad:
        top = btn_grad[0]
        mid = btn_grad[1]
        bot = btn_grad[2]
        grad = vgrad((w, h), [(0.0, top + (255,)), (0.5, mid + (255,)), (1.0, bot + (255,))])
    else:
        ta = P['panel_top'] if top_a is None else top_a
        ba = P['panel_bot'] if bot_a is None else bot_a
        tt = (255, 255, 255) if tint is None else tint
        grad = vgrad((w, h), [(0.0, tt + (int(255 * ta),)),
                              (1.0, (GLASS_TINT if tint is None else tint) + (int(255 * ba),))])
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    layer.paste(grad, (0, 0), mask)

    # 4) 顶部柔光带
    gloss = vgrad((w, h), [(0.0, (255, 255, 255, int(255 * P['gloss_a']))),
                           (P['gloss_mid_at'], (255, 255, 255, int(255 * P['gloss_mid']))),
                           (P['gloss_end'], (255, 255, 255, 0))])
    layer = Image.alpha_composite(layer, Image.composite(gloss, Image.new("RGBA", (w, h), (0, 0, 0, 0)), mask))

    # 5) 底部内阴影
    shade = vgrad((w, h), [(0.0, (0, 0, 0, 0)),
                          (P['shade_from'], (0, 0, 0, 0)),
                          (1.0, EDGE_DEEP + (int(255 * P['shade_a']),))])
    layer = Image.alpha_composite(layer, Image.composite(shade, Image.new("RGBA", (w, h), (0, 0, 0, 0)), mask))

    # 6) 左上镜面光斑
    r = min(w, h) * P['spec_r']
    sp = radial((w, h), (w * P['spec_cx'], h * P['spec_cy']), r, P['spec_a'])
    layer = Image.alpha_composite(layer, Image.composite(sp, Image.new("RGBA", (w, h), (0, 0, 0, 0)), mask))

    # 贴回
    base_img.paste(layer, (int(x0), int(y0)), layer)

    # 7) 高光描边
    d = ImageDraw.Draw(base_img)
    ea = P['panel_edge'] if edge_a is None else edge_a
    bea = P['btn_edge'] if btn_edge_a is None else btn_edge_a
    use = bea if (is_button and btn_edge_a is None) else ea
    d.rounded_rectangle([x0, y0, x1, y1], radius=radius,
                        outline=(255, 255, 255, int(255 * use)),
                        width=int(1.4 * DP) if is_button else int(1.2 * DP))
    return base_img


def lighten(c, f):
    return tuple(int(round(ch + (255 - ch) * f)) for ch in c)


def darken(c, f):
    return tuple(int(round(ch * (1 - f))) for ch in c)


def load_font(sz, bold=False):
    for path in [r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
                 r"C:\Windows\Fonts\simhei.ttf"]:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, sz)
            except Exception:
                pass
    return ImageFont.load_default()


def render_block(P, y0, title, f_title, f_body, f_gear=None):
    """渲染一整块（背景 + 标签 + 面板 + 三个按钮）"""
    bh = 780
    img = make_bg(W, bh)
    d = ImageDraw.Draw(img)

    d.text((40, 26), title, font=f_title, fill=(14, 46, 63, 255))
    d.line([(40, 74), (W - 40, 74)], fill=(255, 255, 255, 160), width=2)

    # 玻璃面板（对话框样式）
    img = glass_shape(img, (40, 100, W - 40, 380), int(P['panel_r'] if 'panel_r' in P else 26 * DP), P)

    d2 = ImageDraw.Draw(img)
    d2.text((76, 136), "添加好友", font=f_title, fill=TEXT_DARK + (255,))
    d2.text((76, 190), "对方用户名", font=f_body, fill=(58, 124, 165, 255))
    d2.rounded_rectangle([76, 220, W - 76, 280], radius=int(16 * DP),
                         fill=(255, 255, 255, int(255 * 0.58)),
                         outline=(30, 159, 216, int(255 * 0.55)), width=int(1.3 * DP))
    d2.text((92, 236), "输入对方用户名…", font=f_body, fill=(58, 124, 165, 190))

    # 主按钮
    bx = 470
    img = glass_shape(img, (bx, 306, W - 76, 358), int(P['btn_r'] * DP), P, is_button=True,
                btn_grad=(lighten(PRIMARY, P['btn_lighten']), PRIMARY, darken(PRIMARY, P['btn_darken'])))
    d3 = ImageDraw.Draw(img)
    tw = d3.textbbox((0, 0), "添加", font=f_body)[2]
    d3.text((bx + (W - 76 - bx - tw) / 2, 320), "添加", font=f_body, fill=(255, 255, 255, 255))

    # 取消（文字按钮）
    img = glass_shape(img, (330, 306, 452, 358), int(30 * DP), P, top_a=P['tb_top'], bot_a=P['tb_bot'],
                edge_a=P['tb_edge'])
    d3.text((352, 320), "取消", font=f_body, fill=PRIMARY + (255,))

    # 图标按钮（圆形）
    img = glass_shape(img, (76, 300, 136, 360), 30 * DP, P, top_a=P['ib_top'], bot_a=P['ib_bot'],
                edge_a=P['ib_edge'])
    d3.text((90, 312), "\u2699", font=f_gear, fill=PRIMARY + (255,))

    # 次按钮（描边款）
    img = glass_shape(img, (156, 300, 312, 360), int(P['btn_r'] * DP), P, is_button=True,
                top_a=P['out_top'], bot_a=P['out_bot'])
    d3.text((176, 316), "重新生成", font=f_body, fill=PRIMARY + (255,))

    d4 = ImageDraw.Draw(img)
    for i, line in enumerate(P['notes']):
        d4.text((40, 400 + i * 32), line, font=f_body, fill=(58, 124, 165, 220))
    return img


def main():
    f_title = load_font(30, bold=True)
    f_body = load_font(24)

    OLD['panel_r'] = 24
    NEW['panel_r'] = 26
    OLD['notes'] = [
        "面板圆角 24dp · 主按钮圆角 16dp · 顶部反光带较宽 · 镜面光斑大而散",
        "面板底透明 0.90 / 底部 0.68 · 顶部反光 0.55 · 光斑半径 0.75",
        "观感：偏厚实、发白，背景水纹透出较少",
    ]
    NEW['notes'] = [
        "面板圆角 26dp · 主按钮圆角 20dp · 反光带收窄 · 镜面光斑小而聚拢",
        "面板底透明 0.72 / 底部 0.40 · 顶部反光 0.62 · 光斑半径 0.60",
        "观感：更通透更有厚度，背景水纹明显透出",
    ]

    f_gear = load_font(26)
    top = render_block(OLD, 0, "调档前 · BEFORE", f_title, f_body, f_gear)
    bot = render_block(NEW, 780, "调档后 · AFTER（更通透 / 反光更聚拢 / 更圆润）", f_title, f_body, f_gear)

    canvas = Image.new("RGBA", (W, 1560), (255, 255, 255, 255))
    canvas.paste(top, (0, 0), top)
    canvas.paste(bot, (0, 780), bot)
    out = r"C:\Users\27443\WorkBuddy\2026-10-28-22-39-51\pet-chat-app\design\waterdrop_mascot\glass_tuning_compare.png"
    canvas.convert("RGB").save(out, "PNG")
    print("saved", out)


if __name__ == "__main__":
    main()
