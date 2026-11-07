# -*- coding: utf-8 -*-
"""渲染「简洁美观」聊天界面示意预览（透明水纹主题 + 3D 玻璃气泡/按钮）。"""
import math
from PIL import Image, ImageDraw, ImageFont

W, H = 540, 1170
DP = W / 360.0  # 模拟 360dp 宽

def font(sz, bold=False):
    paths = [
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/msyhbd.ttc",
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    try:
        idx = 1 if bold else 0
        return ImageFont.truetype(paths[idx], int(sz))
    except Exception:
        return ImageFont.load_default()

def lerp(a, b, t): return tuple(int(a[i] + (b[i]-a[i])*t) for i in range(len(a)))

def vgrad(size, stops):
    """stops: list of (pos, color)"""
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    stops = sorted(stops)
    for y in range(h):
        t = y / max(1, h-1)
        c = stops[0][1]
        for i in range(len(stops)-1):
            p0, c0 = stops[i]; p1, c1 = stops[i+1]
            if p0 <= t <= p1:
                tt = 0 if p1 == p0 else (t-p0)/(p1-p0)
                c = lerp(c0, c1, tt); break
        for x in range(w):
            px[x, y] = c
    return img

# ---------- 背景：透明水纹 ----------
bg = vgrad((W, H), [(0.0, (234,247,255,255)), (0.5, (191,231,255,255)), (1.0, (155,214,255,255))])
# 水波纹圆环
d = ImageDraw.Draw(bg)
cx, cy = W*0.72, H*0.18
for r in (W*0.34, W*0.46):
    ring = Image.new("RGBA", (W,H), (0,0,0,0))
    rd = ImageDraw.Draw(ring)
    bw = int(2*DP)
    rd.ellipse([cx-r, cy-r, cx+r, cy+r], outline=(255,255,255,40), width=bw)
    bg = Image.alpha_composite(bg, ring)

canvas = bg.copy()
cd = ImageDraw.Draw(canvas)

def round_rect(draw, box, r, fill=None, outline=None, width=1):
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)

def glass_panel(box, r, top=(255,255,255,183), bot=(220,239,251,102), edge=(255,255,255,242), elev=10):
    """模拟 3D 玻璃面板：渐变底 + 顶部高光 + 圆角 + 高光描边"""
    x0,y0,x1,y1 = box
    # 投影
    sh = Image.new("RGBA", (W,H), (0,0,0,0))
    sd = ImageDraw.Draw(sh)
    sd.rounded_rectangle([x0+elev*0.4, y0+elev*0.6, x1+elev*0.4, y1+elev*0.6], radius=r, fill=(30,159,216,46))
    canvas.alpha_composite(sh, (0,0))
    # 渐变玻璃底
    p = vgrad((int(x1-x0), int(y1-y0)), [(0.0, top), (1.0, bot)])
    canvas.paste(p, (int(x0), int(y0)), p)
    # 顶部柔光带
    hl = Image.new("RGBA", (int(x1-x0), int(y1-y0)), (0,0,0,0))
    hd = ImageDraw.Draw(hl)
    hr = int(r)
    hd.rounded_rectangle([0,0, int(x1-x0), int((y1-y0)*0.42)], radius=hr, fill=(255,255,255,150))
    canvas.paste(hl, (int(x0), int(y0)), hl)
    # 高光描边
    cd.rounded_rectangle(box, radius=r, outline=edge, width=1)

def glass_circle(cx, cy, rad, top=(255,255,255,168), bot=(255,255,255,66), edge=(255,255,255,209), elev=6):
    # 投影
    sh = Image.new("RGBA", (W,H), (0,0,0,0))
    sd = ImageDraw.Draw(sh)
    sd.ellipse([cx-rad+2, cy-rad+elev*0.6, cx+rad+2, cy+rad+elev*0.6], fill=(30,159,216,40))
    canvas.alpha_composite(sh, (0,0))
    # 渐变圆
    p = Image.new("RGBA", (rad*2, rad*2), (0,0,0,0))
    pd = ImageDraw.Draw(p)
    pd.ellipse([0,0,rad*2,rad*2], fill=bot)
    pd.ellipse([0,0,rad*2,rad*2*0.9], fill=top)
    canvas.paste(p, (int(cx-rad), int(cy-rad)), p)
    cd.ellipse([cx-rad, cy-rad, cx+rad, cy+rad], outline=edge, width=1)

# ---------- 顶栏 ----------
tb_h = 64*DP
# 返回圆形按钮
glass_circle(int(28*DP), int(tb_h/2), int(17*DP), (255,255,255,180), (255,255,255,70), (255,255,255,220))
cd.text((int(28*DP)-8, int(tb_h/2)-13), "‹", font=font(26, True), fill=(30,159,216,255))
# 头像（水蓝渐变 + 字母）
ax, ay, ar = int(56*DP), int(tb_h/2), int(19*DP)
av = Image.new("RGBA", (ar*2, ar*2), (0,0,0,0))
avd = ImageDraw.Draw(av)
avd.ellipse([0,0,ar*2,ar*2], fill=(30,159,216,255))
avd.ellipse([0,0,ar*2,ar*2*0.7], fill=(86,198,224,255))
canvas.paste(av, (int(ax-ar), int(ay-ar)), av)
cd.text((ax-9, ay-12), "布", font=font(22, True), fill=(255,255,255,255))
# 名字 + 在线
cd.text((int(82*DP), int(tb_h/2-15)), "布丁", font=font(17, True), fill=(14,46,63,255))
cd.ellipse([int(82*DP), int(tb_h/2+8), int(82*DP)+7, int(tb_h/2+15)], fill=(76,217,100,255))
cd.text((int(92*DP), int(tb_h/2+3)), "在线", font=font(11), fill=(58,124,165,230))

# ---------- 消息区 ----------
def bubble(left, top, text, mine):
    f = font(14.5)
    pad = 11*DP
    maxw = 200*DP
    chars = list(text)
    lines = []
    cur = ""
    cw = 0
    for ch in chars:
        bw = f.getlength(ch)
        if cw + bw > maxw:
            lines.append(cur); cur = ch; cw = bw
        else:
            cur += ch; cw += bw
    if cur: lines.append(cur)
    lh = 20*DP
    tw = max(f.getlength(l) for l in lines)
    bw = tw + pad*2
    bh = lh*len(lines) + pad*2 - (lh-14*DP)
    r = 18*DP
    if mine:
        bx1 = left; bx0 = left - bw
        box = [bx0, top, bx1, top+bh]
        glass_panel(box, r, top=(30,159,216,235), bot=(26,135,180,225), edge=(255,255,255,200), elev=6)
        text_x = bx0 + pad
    else:
        box = [left, top, left+bw, top+bh]
        glass_panel(box, r, top=(255,255,255,183), bot=(255,255,255,128), edge=(255,255,255,200), elev=6)
        text_x = left + pad
    for i, l in enumerate(lines):
        cd.text((int(text_x), int(top + (pad-7*DP) + i*lh)), l, font=f, fill=(255,255,255,255) if mine else (14,46,63,255))
    return top + bh

# 时间戳
def timestamp(y, t):
    cd.text((W//2-22, y), t, font=font(11), fill=(58,124,165,210))
    return y + 22*DP

y = tb_h + 14*DP
y = timestamp(y, "18:30")
# 对方消息（带头像）
msg_y = y
av2 = Image.new("RGBA", (int(30*DP)*2, int(30*DP)*2), (0,0,0,0))
a2d = ImageDraw.Draw(av2)
a2d.ellipse([0,0,int(30*DP)*2,int(30*DP)*2], fill=(30,159,216,255))
a2d.ellipse([0,0,int(30*DP)*2,int(30*DP)*2*0.7], fill=(86,198,224,255))
canvas.paste(av2, (int(12*DP), int(msg_y)), av2)
cd.text((int(12*DP)+int(30*DP)/2-9, int(msg_y)+int(30*DP)/2-12), "布", font=font(20,True), fill=(255,255,255,255))
bubble(int(12*DP)+int(30*DP)+6*DP, msg_y, "嗨，你的宠物今天好吗？", mine=False)
y = msg_y + 52*DP + 8*DP

cd.text((W//2-22, y), "18:31", font=font(11), fill=(58,124,165,210))
y += 22*DP
# 己方消息
bubble(W-12*DP, y, "它刚学会了一个新表情！", mine=True)
y += 52*DP + 8*DP
y = bubble(W-12*DP, y, "你看，这是它摇尾巴的样子～", mine=True)
y += 52*DP + 14*DP

cd.text((W//2-22, y), "18:32", font=font(11), fill=(58,124,165,210))
y += 22*DP
bubble(int(12*DP)+int(30*DP)+6*DP, y, "好可爱！我也想养一只了。", mine=False)

# ---------- 输入栏 ----------
ib_y = H - 64*DP
# 胶囊输入框
glass_panel([12*DP, ib_y, W-70*DP, ib_y+44*DP], 22*DP, top=(255,255,255,148), bot=(255,255,255,92), edge=(255,255,255,200), elev=4)
cd.text((24*DP, ib_y+14*DP), "发条消息…", font=font(14), fill=(120,150,170,160))
# 圆形发送按钮（主色玻璃）
scx, scy, srad = int(W-40*DP), int(ib_y+22*DP), int(22*DP)
glass_circle(scx, scy, srad, top=(120,210,240,255), bot=(22,120,165,255), edge=(255,255,255,230), elev=8)
cd.polygon([(scx-7, scy-7), (scx+10, scy), (scx-7, scy+7)], fill=(255,255,255,255))

canvas = canvas.convert("RGB")
canvas.save("chat_preview.png")
print("saved chat_preview.png", canvas.size)
