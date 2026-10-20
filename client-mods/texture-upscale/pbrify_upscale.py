"""Upscale a folder of PNG tiles 4x with a spandrel-supported model (e.g. 4x-PBRify_RPLKSRd_V3) on CUDA.

Run with the separate tools environment that has torch(CUDA)+spandrel:
    <tools>/pbrify/venv/Scripts/python.exe -I pbrify_upscale.py --model <pth> --input <dir> --output <dir>
Existing outputs are skipped, so the run can be resumed.
"""
import argparse
from pathlib import Path
import numpy as np
import torch
import spandrel
from PIL import Image


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--input', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if not torch.cuda.is_available(): raise SystemExit('CUDA is not available to torch')
    desc = spandrel.ModelLoader().load_from_file(str(args.model))
    model = desc.model.cuda().eval()
    half = desc.supports_half
    if half: model = model.half()
    args.output.mkdir(parents=True, exist_ok=True)
    files = sorted(f for f in args.input.glob('*.png') if not (args.output / f.name).exists())
    for i, f in enumerate(files):
        image = np.asarray(Image.open(f).convert('RGB'), dtype=np.float32) / 255.0
        tensor = torch.from_numpy(image).permute(2, 0, 1).unsqueeze(0).cuda()
        if half: tensor = tensor.half()
        with torch.inference_mode():
            result = model(tensor).float().clamp(0, 1)[0].permute(1, 2, 0).cpu().numpy()
        Image.fromarray((result * 255.0 + 0.5).astype(np.uint8)).save(args.output / f.name)
        if i % 25 == 0: print(f'{i + 1}/{len(files)}', flush=True)
    print(f'Upscaled {len(files)} textures with {desc.architecture.name} x{desc.scale}', flush=True)


if __name__ == '__main__':
    main()
