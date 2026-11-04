"""Decode 16x16 RGBA icons (PNG colour types 2/6) and compose a scaled contact sheet."""
import struct, zlib, sys

def read_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    idat = b''
    w = h = ct = bd = 0
    pal = None
    while pos < len(data):
        ln, typ = struct.unpack('>I4s', data[pos:pos+8])
        chunk = data[pos+8:pos+8+ln]
        pos += 12 + ln
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', chunk[:10])
        elif typ == b'PLTE':
            pal = chunk
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
    raw = zlib.decompress(idat)
    chans = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    if bd == 16:
        chans *= 2
    bpp = chans
    stride = (w * chans * bd + 7) // 8
    out = bytearray()
    prev = bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i-bpp]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                c = prev[i-bpp] if i >= bpp else 0
                b = prev[i]
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        prev = line
        if bd == 16:
            line = bytearray(line[0::2])
        elif bd < 8 and ct == 3:
            packed = line
            line = bytearray(w)
            per = 8 // bd
            mask = (1 << bd) - 1
            for x in range(w):
                byte = packed[x // per]
                shift = 8 - bd * (x % per + 1)
                line[x] = (byte >> shift) & mask
        for x in range(w):
            px = line[x*bpp:(x+1)*bpp]
            if bd == 16:
                px = line[x*bpp:(x+1)*bpp]
            if ct == 6: out += bytes(px)
            elif ct == 2: out += bytes(px) + b'\xff'
            elif ct == 3:
                i = px[0] * 3
                out += pal[i:i+3] + b'\xff'
            elif ct == 0: out += bytes(px)*3 + b'\xff'
            else: out += bytes(px[:1])*3 + (px[1:2] if len(px) > 1 else b'\xff')
    return w, h, bytes(out)

def write_png(path, w, h, rgba):
    raw = b''.join(b'\x00' + rgba[y*w*4:(y+1)*w*4] for y in range(h))
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t+d) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)

def sheet(paths, out, scale=6, cols=6, cell=16, bg=(30, 26, 42, 255)):
    rows = (len(paths) + cols - 1) // cols
    W, H = cols * (cell * scale + 4), rows * (cell * scale + 4)
    buf = bytearray()
    for _ in range(W*H):
        buf += bytes(bg)
    for idx, p in enumerate(paths):
        try:
            w, h, px = read_png(p)
        except Exception as e:
            print('skip', p, e); continue
        ox = (idx % cols) * (cell*scale+4) + 2
        oy = (idx // cols) * (cell*scale+4) + 2
        for y in range(min(h, cell)):
            for x in range(min(w, cell)):
                s = (y*w+x)*4
                r, g, b, a = px[s], px[s+1], px[s+2], px[s+3]
                for dy in range(scale):
                    for dx in range(scale):
                        d = ((oy+y*scale+dy)*W + ox+x*scale+dx)*4
                        if a == 255:
                            buf[d:d+4] = bytes((r, g, b, 255))
                        elif a:
                            buf[d:d+3] = bytes((r, g, b)); buf[d+3] = a
    write_png(out, W, H, bytes(buf))

if __name__ == '__main__':
    args = sys.argv[1:]
    out = args[0]
    sheet(args[1:], out)
    print('wrote', out)
