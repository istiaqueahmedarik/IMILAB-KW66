#!/usr/bin/env python3
"""Static vector design specimen. This is NOT a screenshot or Android runtime test."""
from pathlib import Path
from html import escape
root=Path(__file__).resolve().parents[1]
out=['''<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1090" viewBox="0 0 1000 1090">
<defs>
<linearGradient id="glass" x2="1" y2="1"><stop stop-color="#314c50" stop-opacity=".8"/><stop offset="1" stop-color="#102b31" stop-opacity=".94"/></linearGradient>
<linearGradient id="hero" x2="1" y2="1"><stop stop-color="#3c6867" stop-opacity=".8"/><stop offset="1" stop-color="#122e34" stop-opacity=".94"/></linearGradient>
<radialGradient id="glow"><stop stop-color="#3f7471" stop-opacity=".7"/><stop offset="1" stop-color="#08191d" stop-opacity="0"/></radialGradient>
<clipPath id="phone"><rect width="412" height="890" rx="36"/></clipPath>
</defs><rect width="1000" height="1090" fill="#061519"/>
<g font-family="DejaVu Sans, sans-serif" fill="#ebf6f1">
<text x="64" y="48" font-size="22">KW66 Glass</text><text x="64" y="76" font-size="13" fill="#97b5b1">Static design specimen · native implementation in app/ · no device screenshot</text>''']
def text(x,y,s,size=14,fill='#ebf6f1',weight='normal'):
    out.append(f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" font-weight="{weight}">{escape(s)}</text>')
def panel(y,h,accent=False):out.append(f'<rect x="20" y="{y}" width="372" height="{h}" rx="28" fill="url(#{"hero" if accent else "glass"})" stroke="#d9fff1" stroke-opacity=".2"/>')
def btn(x,y,w,s,primary=False):
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="48" rx="16" fill="{"#a8e6d3" if primary else "#2b5053"}"/>')
    out.append(f'<text x="{x+w/2}" y="{y+29}" text-anchor="middle" font-size="14" fill="{"#08191d" if primary else "#ebf6f1"}">{escape(s)}</text>')
for idx,x in enumerate([64,524]):
    out.append(f'<g transform="translate({x} 116)"><rect x="-5" y="-5" width="422" height="900" rx="41" fill="#132a30"/><g clip-path="url(#phone)"><rect width="412" height="890" fill="#08191d"/><ellipse cx="380" cy="150" rx="390" ry="400" fill="url(#glow)"/>')
    text(24,34,'9:41',12);text(315,34,'●  ▰  ▰',11)
    text(24,76,'KW66  /  GLASS',12,'#97b5b1');text(300,76,'LOCAL FIRST',10,'#97b5b1')
    text(20,132,'Your day, in rhythm.' if idx==0 else 'Make room for today.',29,weight='500')
    text(20,160,'Small signals. A clearer day.' if idx==0 else 'Reminders that stay close.',14,'#97b5b1')
    if idx==0:
        panel(190,246,True);text(40,224,'IMILAB KW66',12,'#97b5b1');text(40,269,'Not connected',20);text(40,294,'Choose your watch',13,'#97b5b1');text(40,316,'to get started',13,'#97b5b1')
        out.append('<g transform="translate(300 288)"><rect x="-18" y="-78" width="36" height="156" rx="12" fill="#17383c"/><circle r="55" fill="#10292f" stroke="#c3f1e6" stroke-opacity=".4" stroke-width="2"/><circle r="45" fill="none" stroke="#3b7975" stroke-opacity=".4" stroke-width="6"/></g>')
        text(264,293,'KW66',25);text(262,312,'AWAITING',9,'#97b5b1');btn(40,368,162,'Sync history',True);btn(210,368,162,'Watch settings')
        panel(452,214);text(40,487,'TODAY’S SIGNALS',12,'#97b5b1');text(40,537,'— steps',36);text(40,561,'Recorded hourly steps',13,'#97b5b1')
        text(40,604,'Heart rate',13,'#97b5b1');text(212,604,'Oxygen',13,'#97b5b1');text(40,636,'— bpm',24);text(212,636,'— %',24)
        panel(682,166);text(40,716,'UP NEXT',12,'#97b5b1');text(40,752,'A little breathing room',20);text(40,778,'Your next reminder will appear here.',13,'#97b5b1')
    else:
        panel(190,202,True);text(40,229,'One less thing',23);text(40,259,'to remember',23);text(40,292,'Schedule the things that matter.',13,'#97b5b1');btn(40,324,162,'Add reminder',True);btn(210,324,162,'Add alarm')
        panel(408,202);text(40,443,'REMINDER / TODO',12,'#97b5b1');text(40,482,'Stretch and take a break',20);text(40,510,'Tomorrow, 10:00 AM · every day',13,'#97b5b1');btn(40,540,332,'Mark done / cancel')
        panel(626,208);text(40,662,'ALARM',12,'#97b5b1');text(40,701,'Morning, at your pace',22);text(40,731,'Tomorrow, 7:00 AM',13,'#97b5b1');text(40,765,'Keeps ringing until you stop it',13,'#97b5b1');text(40,787,'in KW66 Glass.',13,'#97b5b1')
    out.append('<rect x="0" y="818" width="412" height="72" rx="24" fill="url(#glass)"/>')
    for i,name in enumerate(['Today','Health','Plan','Watch','AI']):
        if i==(0 if idx==0 else 2):out.append(f'<rect x="{10+i*79}" y="827" width="76" height="48" rx="16" fill="#a8e6d3"/>')
        text(23+i*79,857,name,12,'#08191d' if i==(0 if idx==0 else 2) else '#ebf6f1')
    out.append('</g></g>')
text(64,1052,'TODAY · empty state',12,'#97b5b1');text(524,1052,'PLAN · illustrative reminder data',12,'#97b5b1');out.append('</g></svg>')
target=root/'docs/design-preview.svg';target.write_text('\n'.join(out))
try:
    import ctypes as C,ctypes.util
    rsvg=C.CDLL(ctypes.util.find_library('rsvg-2'));cairo=C.CDLL(ctypes.util.find_library('cairo'));gobject=C.CDLL(ctypes.util.find_library('gobject-2.0'))
    class Rect(C.Structure):_fields_=[('x',C.c_double),('y',C.c_double),('width',C.c_double),('height',C.c_double)]
    rsvg.rsvg_handle_new_from_file.argtypes=[C.c_char_p,C.c_void_p];rsvg.rsvg_handle_new_from_file.restype=C.c_void_p
    rsvg.rsvg_handle_render_document.argtypes=[C.c_void_p,C.c_void_p,C.POINTER(Rect),C.c_void_p];rsvg.rsvg_handle_render_document.restype=C.c_int
    cairo.cairo_image_surface_create.argtypes=[C.c_int,C.c_int,C.c_int];cairo.cairo_image_surface_create.restype=C.c_void_p
    cairo.cairo_create.argtypes=[C.c_void_p];cairo.cairo_create.restype=C.c_void_p
    cairo.cairo_surface_write_to_png.argtypes=[C.c_void_p,C.c_char_p];cairo.cairo_surface_write_to_png.restype=C.c_int
    cairo.cairo_destroy.argtypes=[C.c_void_p];cairo.cairo_surface_destroy.argtypes=[C.c_void_p];gobject.g_object_unref.argtypes=[C.c_void_p]
    surface=cairo.cairo_image_surface_create(0,1000,1090);ctx=cairo.cairo_create(surface);handle=rsvg.rsvg_handle_new_from_file(str(target).encode(),None)
    if not handle or not rsvg.rsvg_handle_render_document(handle,ctx,C.byref(Rect(0,0,1000,1090)),None):raise RuntimeError('SVG render failed')
    if cairo.cairo_surface_write_to_png(surface,str(root/'docs/design-preview.png').encode()):raise RuntimeError('PNG write failed')
    gobject.g_object_unref(handle);cairo.cairo_destroy(ctx);cairo.cairo_surface_destroy(surface)
except (OSError,ImportError):print('SVG written; optional librsvg/cairo unavailable for PNG.')
print(target)
