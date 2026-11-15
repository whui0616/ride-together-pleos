import os, time
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
LABELS = ["food", "rest", "route", "message", "bored", "control", "mood", "greeting", "other"]

KEYWORDS = [
    ("control", ["창문", "에어컨", "히터", "문열", "문닫", "문좀", "트렁크", "시동", "선루프", "와이퍼켜", "와이퍼꺼", "열선"]),
    ("route", ["도착", "언제가", "얼마나걸", "얼마나남", "몇시", "남았", "몇분", "몇킬로"]),
    ("message", ["메시지", "문자", "카톡", "답장", "연락"]),
    ("rest", ["졸려", "졸리", "졸음", "피곤", "눈꺼풀", "하품", "눈이감", "눈감겨", "쉬고", "쉬자", "쉬었다", "휴게소", "화장실", "잠와", "잠이와"]),
    ("food", ["배고", "배가고", "출출", "밥", "먹을", "먹고", "맛집", "커피", "간식", "햄버거"]),
    ("bored", ["심심", "이야기", "얘기", "재밌", "재미있", "노래", "퀴즈"]),
    ("mood", ["우울", "슬퍼", "슬프", "짜증", "화나", "스트레스", "힘들", "속상", "기분"]),
    ("greeting", ["안녕", "고마", "감사", "잘했", "최고", "반가"]),
]

def keyword(u):
    t = u.replace(" ", "")
    for lab, words in KEYWORDS:
        if any(w in t for w in words):
            return lab
    return None

def load(name):
    rows = []
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line:
                lab, text = line.split("\t", 1)
                rows.append((text, lab))
    return rows

def report(name, test, predict_fn, extra=""):
    texts = [t for t, _ in test]
    gold = [l for _, l in test]
    t0 = time.perf_counter()
    pred = predict_fn(texts)
    dt = (time.perf_counter() - t0) / len(texts) * 1000
    kw = [keyword(t) for t in texts]
    acc_all = sum(p == g for p, g in zip(pred, gold)) / len(gold)
    miss = [i for i, k in enumerate(kw) if k is None]
    acc_miss = sum(pred[i] == gold[i] for i in miss) / len(miss)
    pipe = [k or p for k, p in zip(kw, pred)]
    acc_pipe = sum(p == g for p, g in zip(pipe, gold)) / len(gold)
    print(f"[{name}] 모델만 {acc_all:.1%} | 낱말이 놓친 {len(miss)}개 {acc_miss:.1%} | 낱말→모델 {acc_pipe:.1%} | {dt:.1f}ms/문장 {extra}")
    wrong = Counter((gold[i], pred[i]) for i in miss if pred[i] != gold[i])
    print("   놓친 문장 오답(정답→예측):", ", ".join(f"{g}→{p}×{n}" for (g, p), n in wrong.most_common(8)))
    return dict(name=name, acc_all=acc_all, acc_miss=acc_miss, acc_pipe=acc_pipe, ms=dt,
                wrong=[(texts[i], gold[i], pred[i]) for i in miss if pred[i] != gold[i]])
