import sys, time, os
import numpy as np
from common import load, report, LABELS
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression
import torch

torch.set_num_threads(1)
path, prefix = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "")
name = os.path.basename(path.rstrip("/\\"))
m = SentenceTransformer(path, device="cpu")
train, test = load("train.tsv"), load("test.tsv")
enc = lambda xs: m.encode([prefix + x for x in xs], normalize_embeddings=True, batch_size=1)
Xtr = enc([t for t, _ in train]); ytr = np.array([l for _, l in train])

def knn(xs, k=1):
    E = enc(xs); S = E @ Xtr.T
    out = []
    for s in S:
        idx = np.argsort(-s)[:k]
        votes = {}
        for i in idx: votes[ytr[i]] = votes.get(ytr[i], 0) + s[i]
        out.append(max(votes, key=votes.get))
    return out

cent = np.stack([Xtr[ytr == l].mean(0) for l in LABELS]); cent /= np.linalg.norm(cent, axis=1, keepdims=True)
clf = LogisticRegression(C=10, max_iter=3000).fit(Xtr, ytr)
params = sum(p.numel() for p in m.parameters()) / 1e6
report(f"{name} 최근접1", test, knn, f"{params:.0f}M")
report(f"{name} 최근접5", test, lambda xs: knn(xs, 5))
report(f"{name} 분류평균", test, lambda xs: [LABELS[i] for i in (enc(xs) @ cent.T).argmax(1)])
report(f"{name} 로지스틱", test, lambda xs: list(clf.predict(enc(xs))))
