import sys, json
from common import load, keyword, report, LABELS
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

train, test = load("train.tsv"), load("test.tsv")
tt = set(t for t, _ in train)
print("학습", len(train), "시험", len(test), "겹침", sum(t in tt for t, _ in test))
kw = [keyword(t) for t, _ in test]
print("낱말 규칙이 잡은 시험 문장", sum(k is not None for k in kw), "개, 그중 맞음", sum(k == g for k, (_, g) in zip(kw, test) if k))

report("지금 앱(낱말만, 못 잡으면 other)", test, lambda xs: [keyword(x) or "other" for x in xs])

for ng in [(1, 2), (1, 3), (2, 4)]:
    vec = TfidfVectorizer(analyzer="char_wb", ngram_range=ng, sublinear_tf=True)
    X = vec.fit_transform([t for t, _ in train])
    clf = LogisticRegression(C=10, max_iter=2000).fit(X, [l for _, l in train])
    report(f"글자 n-gram{ng}+로지스틱", test, lambda xs: list(clf.predict(vec.transform(xs))),
           f"특징 {len(vec.vocabulary_)}개 ≈ {len(vec.vocabulary_)*9*4/1024:.0f}KB")
