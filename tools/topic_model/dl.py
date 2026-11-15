import sys, os
from huggingface_hub import snapshot_download
repo = sys.argv[1]
dst = os.path.join("D:/nlu/models", repo.split("/")[1])
os.makedirs(dst, exist_ok=True)
snapshot_download(repo, local_dir=dst, allow_patterns=["*.json", "*.txt", "*.safetensors", "*.model", "sentencepiece*", "1_Pooling/*", "modules.json", "*.py"] if "--bin" not in sys.argv else None,
                  ignore_patterns=["onnx/*", "openvino/*", "*.onnx", "*.h5", "*.msgpack", "*.ot"])
print(repo, "->", dst, sum(os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(dst) for f in fs) // 2**20, "MB")
