import sys, os, random
import numpy as np, torch
from transformers import AutoTokenizer, AutoModel
from common import load, report, LABELS

args = [a for a in sys.argv[1:]]
save = None
if "--save" in args:
    i = args.index("--save"); save = args[i + 1]; del args[i:i + 2]
path, epochs, lr = args[0], int(args[1]), float(args[2])
seeds = [int(s) for s in args[3:]] or [0]
name = os.path.basename(path.rstrip("/\\"))
train, test = load("train.tsv"), load("test.tsv")
tok = AutoTokenizer.from_pretrained(path)
torch.set_num_threads(4)

class Clf(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.enc = AutoModel.from_pretrained(path)
        self.drop = torch.nn.Dropout(0.1)
        self.head = torch.nn.Linear(self.enc.config.hidden_size, len(LABELS))

    def forward(self, input_ids, attention_mask):
        h = self.enc(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state
        m = attention_mask.unsqueeze(-1).float()
        return self.head(self.drop((h * m).sum(1) / m.sum(1)))

def batches(rows, bs):
    rows = rows[:]; random.shuffle(rows)
    for i in range(0, len(rows), bs):
        b = rows[i:i + bs]
        enc = tok([t for t, _ in b], padding=True, return_tensors="pt")
        yield enc, torch.tensor([LABELS.index(l) for _, l in b])

res = []
for seed in seeds:
    random.seed(seed); np.random.seed(seed); torch.manual_seed(seed)
    model = Clf()
    opt = torch.optim.AdamW([
        {"params": model.enc.parameters(), "lr": lr},
        {"params": model.head.parameters(), "lr": lr * 20}], weight_decay=0.01)
    steps = epochs * ((len(train) + 15) // 16)
    sched = torch.optim.lr_scheduler.LambdaLR(opt, lambda s: min(1.0, (s + 1) / (steps * 0.1)) * max(0.0, 1 - s / steps))
    model.train()
    for ep in range(epochs):
        for enc, y in batches(train, 16):
            loss = torch.nn.functional.cross_entropy(model(enc["input_ids"], enc["attention_mask"]), y, label_smoothing=0.1)
            opt.zero_grad(); loss.backward(); opt.step(); sched.step()
    model.eval()
    torch.set_num_threads(1)

    @torch.no_grad()
    def pred(xs):
        out = []
        for x in xs:
            e = tok(x, return_tensors="pt")
            out.append(LABELS[int(model(e["input_ids"], e["attention_mask"]).argmax())])
        return out
    params = sum(p.numel() for p in model.parameters()) / 1e6
    res.append(report(f"{name} 미세조정 ep{epochs} lr{lr} seed{seed}", test, pred, f"{params:.1f}M"))
    torch.set_num_threads(4)
    if save:
        os.makedirs(save, exist_ok=True)
        torch.save(model.state_dict(), os.path.join(save, f"seed{seed}.pt"))
print(f"== {name} 평균: 모델만 {np.mean([r['acc_all'] for r in res]):.1%} (범위 {min(r['acc_all'] for r in res):.1%}~{max(r['acc_all'] for r in res):.1%}), "
      f"낱말→모델 {np.mean([r['acc_pipe'] for r in res]):.1%}")
