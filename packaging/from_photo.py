import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

src = Image.open(sys.argv[1]).convert('RGB')
rgb = np.asarray(src).astype(float)
bright = rgb.max(-1)

# the background is near-black, so the gem is the biggest bright blob with its holes filled in
mask = bright > 28
labels, n = ndimage.label(mask)
sizes = ndimage.sum(mask, labels, range(1, n + 1))
gem = ndimage.binary_fill_holes(labels == (np.argmax(sizes) + 1))
gem = ndimage.binary_opening(gem, iterations=2)
alpha = Image.fromarray((gem * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))

gray = rgb @ [0.299, 0.587, 0.114]
gray = np.clip((gray - 8) * 1.15, 0, 255)
out = Image.merge('RGBA', [Image.fromarray(gray.astype(np.uint8))] * 3 + [alpha])

box = out.getbbox()
out = out.crop(box)
side = int(max(out.size) * 1.06)
square = Image.new('RGBA', (side, side))
square.alpha_composite(out, ((side - out.width) // 2, (side - out.height) // 2))

icon = square.resize((256, 256), Image.LANCZOS)
icon.save('icon.png')
icon.save('icon.ico', sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
sheet = Image.new('RGBA', (256 + 32 + 128 + 32 + 64, 256), (40, 42, 50, 255))
sheet.alpha_composite(icon, (0, 0))
sheet.alpha_composite(square.resize((32, 32), Image.LANCZOS).resize((128, 128), Image.NEAREST), (288, 64))
sheet.alpha_composite(square.resize((16, 16), Image.LANCZOS).resize((64, 64), Image.NEAREST), (448, 96))
sheet.save('icon_preview.png')
print('ok', box)
