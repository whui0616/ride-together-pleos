import sys, os, time
import numpy as np, torch
from transformers import AutoTokenizer, AutoModel
from sklearn.linear_model import LogisticRegression
import onnxruntime as ort
from onnxruntime.quantization import quantize_dynamic, QuantType
from common import load, report, LABELS

path, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
name = os.path.basename(path.rstrip("/\\"))
tok = AutoTokenizer.from_pretrained(path)
enc = AutoModel.from_pretrained(path).eval()

class Pool(torch.nn.Module):
    def __init__(self, e): super().__init__(); self.e = e
    def forward(self, ids, mask):
        h = self.e(input_ids=ids, attention_mask=mask).last_hidden_state
        m = mask.unsqueeze(-1).float(); v = (h * m).sum(1) / m.sum(1)
        return v / v.norm(dim=1, keepdim=True)

fp = os.path.join(out, "model.onnx"); q8 = os.path.join(out, "model.int8.onnx")
if not os.path.exists(q8):
    e = tok("예시 문장", return_tensors="pt")
    torch.onnx.export(Pool(enc), (e["input_ids"], e["attention_mask"]), fp, input_names=["ids", "mask"], output_names=["vec"],
                      dynamic_axes={"ids": {0: "b", 1: "t"}, "mask": {0: "b", 1: "t"}, "vec": {0: "b"}}, opset_version=17, dynamo=False)
    quantize_dynamic(fp, q8, weight_type=QuantType.QInt8)
train, test = load("train.tsv"), load("test.tsv")
for f in [fp, q8]:
    so = ort.SessionOptions(); so.intra_op_num_threads = 1; so.inter_op_num_threads = 1
    s = ort.InferenceSession(f, so, providers=["CPUExecutionProvider"])
    def vec(xs):
        r = []
        for x in xs:
            e = tok(x, return_tensors="np")
            r.append(s.run(None, {"ids": e["input_ids"].astype(np.int64), "mask": e["attention_mask"].astype(np.int64)})[0][0])
        return np.stack(r)
    Xtr = vec([t for t, _ in train]); clf = LogisticRegression(C=10, max_iter=3000).fit(Xtr, [l for _, l in train])
    vec(["워밍업"])
    mb = os.path.getsize(f) / 2**20
    report(f"{name} {os.path.basename(f)} 로지스틱", test, lambda xs: list(clf.predict(vec(xs))), f"{mb:.0f}MB")
