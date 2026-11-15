import sys, os, shutil
import numpy as np, onnxruntime as ort
from transformers import AutoTokenizer
from sklearn.linear_model import LogisticRegression
from common import load, keyword, LABELS

assets, res = sys.argv[1], sys.argv[2]
M = "D:/nlu/models/ko-sroberta-multitask"
ONNX = "D:/nlu/onnx/ko-sroberta-multitask/model.int8.onnx"
tok = AutoTokenizer.from_pretrained(M)
so = ort.SessionOptions(); so.intra_op_num_threads = 2
s = ort.InferenceSession(ONNX, so)

def ids(x): return tok(x)["input_ids"]
def vec(xs): return np.stack([s.run(None, {"ids": np.array([ids(x)], np.int64), "mask": np.ones((1, len(ids(x))), np.int64)})[0][0] for x in xs])

train, test, hold = load("train.tsv"), load("test.tsv"), load("holdout.tsv")
Xtr, Xte, Xho = vec([t for t, _ in train]), vec([t for t, _ in test]), vec([t for t, _ in hold])

clf = LogisticRegression(C=10, max_iter=3000).fit(Xtr, [l for _, l in train])
P = clf.predict_proba(Xte); cls = list(clf.classes_)
gold = [l for _, l in test]
for th in [0, 0.2, 0.25, 0.3, 0.35, 0.4, 0.5]:
    pred = [cls[p.argmax()] if p.max() >= th else "other" for p in P]
    print(f"문턱 {th}: 시험 {np.mean([a == b for a, b in zip(pred, gold)]):.1%}")

final = LogisticRegression(C=10, max_iter=3000).fit(np.vstack([Xtr, Xte]), [l for _, l in train + test])
order = [list(final.classes_).index(l) for l in LABELS]
W, b = final.coef_[order], final.intercept_[order]
hp = [LABELS[i] for i in (Xho @ W.T + b).argmax(1)]
hg = [l for _, l in hold]
print(f"holdout {len(hold)}문장: 모델만 {np.mean([a == c for a, c in zip(hp, hg)]):.1%}, 낱말→모델 {np.mean([(keyword(t) or p) == g for (t, g), p in zip(hold, hp)]):.1%}")
for (t, g), p in zip(hold, hp):
    if p != g: print("   틀림:", t, g, "→", p)

os.makedirs(assets, exist_ok=True); os.makedirs(res, exist_ok=True)
data = open(ONNX, "rb").read()
CH = 90 * 2**20
for f in os.listdir(assets):
    if f.startswith("ko-sroberta"): os.remove(os.path.join(assets, f))
for i in range(0, len(data), CH):
    open(os.path.join(assets, f"ko-sroberta-multitask.int8.onnx.{i // CH:02d}.bin"), "wb").write(data[i:i + CH])
shutil.copy(os.path.join(M, "vocab.txt"), os.path.join(assets, "vocab.txt"))
with open(os.path.join(assets, "head.tsv"), "w", encoding="utf-8", newline="\n") as f:
    f.write("# 분류\t편향\t가중치 768개 (ko-sroberta-multitask 평균 풀링·정규화 벡터 → 로지스틱 회귀, 학습 414문장)\n")
    for l, w, bb in zip(LABELS, W, b):
        f.write(l + "\t" + repr(float(bb)) + "\t" + " ".join(f"{x:.6g}" for x in w) + "\n")
extra = ["안녕하세요!", "배고파요...", "ㅋㅋㅋ 진짜?", "에어컨 22도로 맞춰줘", "Hello 너 이름이 뭐야?", "3시 25분까지 갈 수 있나", "커피☕ 마시고 싶다", "  띄어  쓰기   많이  ", "뷁뷁 똠얌꿍"]
with open(os.path.join(res, "topic_expected.tsv"), "w", encoding="utf-8", newline="\n") as f:
    f.write("# 문장\t토큰 id(HF BertTokenizerFast)\t파이썬 예측 분류\t정답(없으면 -)\n")
    allrows = [(t, g) for t, g in train + test + hold] + [(t, "-") for t in extra]
    V = np.vstack([Xtr, Xte, Xho, vec(extra)])
    for (t, g), v in zip(allrows, V):
        f.write(f"{t}\t{' '.join(map(str, ids(t)))}\t{LABELS[int((v @ W.T + b).argmax())]}\t{g}\n")
shutil.copy("holdout.tsv", os.path.join(res, "topic_holdout.tsv"))
print("파일:", os.listdir(assets), os.path.getsize(ONNX) // 2**20, "MB")
