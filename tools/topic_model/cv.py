import numpy as np, onnxruntime as ort
from transformers import AutoTokenizer
from sklearn.model_selection import StratifiedKFold
from sklearn.linear_model import LogisticRegression
from sklearn.feature_extraction.text import TfidfVectorizer
from common import load

rows = load("train.tsv") + load("test.tsv")
X = [t for t, _ in rows]; y = np.array([l for _, l in rows])
res = {}
for m in ["ko-sroberta-multitask", "KR-SBERT-V40K-klueNLI-augSTS"]:
    tok = AutoTokenizer.from_pretrained(f"D:/nlu/models/{m}")
    so = ort.SessionOptions(); so.intra_op_num_threads = 2
    s = ort.InferenceSession(f"D:/nlu/onnx/{m}/model.int8.onnx", so)
    E = np.stack([s.run(None, {"ids": (e := tok(x, return_tensors="np"))["input_ids"].astype(np.int64),
                               "mask": e["attention_mask"].astype(np.int64)})[0][0] for x in X])
    res[m] = E
for name in ["글자 n-gram(2,4)", "ko-sroberta-multitask", "KR-SBERT-V40K-klueNLI-augSTS"]:
    accs = []
    for seed in range(3):
        for tr, te in StratifiedKFold(5, shuffle=True, random_state=seed).split(X, y):
            if name.startswith("글자"):
                v = TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 4), sublinear_tf=True)
                A = v.fit_transform([X[i] for i in tr]); B = v.transform([X[i] for i in te])
            else:
                A, B = res[name][tr], res[name][te]
            accs.append((LogisticRegression(C=10, max_iter=3000).fit(A, y[tr]).predict(B) == y[te]).mean())
    print(f"{name}: 5겹×3회 평균 {np.mean(accs):.1%} (최저 {min(accs):.1%}, 최고 {max(accs):.1%})")
