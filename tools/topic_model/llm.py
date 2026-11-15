import sys, os, time
import torch
from transformers import AutoTokenizer, AutoModelForCausalLM
from common import load, report, LABELS

HINT = {"food": "배고픔, 식사, 커피, 간식, 맛집", "rest": "졸림, 피곤함, 휴식, 화장실, 휴게소", "route": "도착 시간, 남은 거리, 경로",
        "message": "문자, 카톡, 메시지, 답장, 연락", "bored": "심심함, 이야기, 노래, 놀이", "control": "창문, 에어컨, 문, 시동 같은 차량 조작 부탁",
        "mood": "기분, 스트레스, 슬픔, 짜증, 힘듦", "greeting": "인사, 고마움, 칭찬", "other": "그 밖의 말"}
EX = [("속이 텅 빈 것 같아", "food"), ("뭐라도 좀 입에 넣고 싶다", "food"), ("눈이 자꾸 감기네", "rest"), ("운전한 지 너무 오래됐어", "rest"),
      ("아직 멀었어?", "route"), ("엄마한테 늦는다고 전해야 하는데", "message"), ("아무 말이나 해 줘", "bored"), ("좀 춥다, 따뜻하게 해 줘", "control"),
      ("오늘 회사에서 혼났어", "mood"), ("너 진짜 귀엽다", "greeting"), ("내일 비 온대", "other")]
SYSTEM = ("운전자의 말을 아래 분류 중 하나로 고른다. 분류 이름(영어 낱말) 하나만 출력하고 다른 말은 쓰지 않는다.\n"
          + "".join(f"{l}: {HINT[l]}\n" for l in LABELS) + "\n예시:\n" + "".join(f"{u} → {t}\n" for u, t in EX)).rstrip()

path = sys.argv[1]
name = os.path.basename(path.rstrip("/\\"))
tok = AutoTokenizer.from_pretrained(path)
model = AutoModelForCausalLM.from_pretrained(path, dtype=torch.float16, device_map="cuda").eval()
lab_ids = [tok(l, add_special_tokens=False)["input_ids"] for l in LABELS]

@torch.no_grad()
def pred(xs):
    out = []
    for x in xs:
        p = tok.apply_chat_template([{"role": "system", "content": SYSTEM}, {"role": "user", "content": x}],
                                    tokenize=False, add_generation_prompt=True, enable_thinking=False)
        pid = tok(p, return_tensors="pt", add_special_tokens=False)["input_ids"][0]
        scores = []
        for ids in lab_ids:
            full = torch.cat([pid, torch.tensor(ids)]).unsqueeze(0).cuda()
            lp = torch.log_softmax(model(full).logits[0, len(pid) - 1:-1].float(), -1)
            scores.append(sum(lp[i, t].item() for i, t in enumerate(ids)))
        out.append(LABELS[max(range(len(LABELS)), key=lambda i: scores[i])])
    return out

print("프롬프트 토큰", len(tok(SYSTEM)["input_ids"]))
report(f"{name} (앱 프롬프트, 9개 중 최고 확률)", load("test.tsv"), pred, f"{sum(p.numel() for p in model.parameters())/1e9:.2f}B, 속도는 GPU라 의미 없음")
