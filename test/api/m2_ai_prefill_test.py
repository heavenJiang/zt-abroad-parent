import json, urllib.request, urllib.parse, sys, time, threading, html
import os
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
# M2-06 项目 AI 预填（catalog_extractor）接口回归
# 被测实例必须以 --abroad.ai.fetch.allow-private-hosts=true 启动（样例页面由本脚本在 127.0.0.1 上提供）；
# 可选 ABR_BASE_STRICT 指向一个 allow-private-hosts=false 的实例，验证内网地址被拦截（未提供时跳过，由单元测试 AbrWebPageFetcherTest 覆盖）
B = os.environ.get("ABR_BASE", "http://localhost:8090")
STRICT = os.environ.get("ABR_BASE_STRICT", "")
def call(method, path, body=None, token=None, base=None):
    req = urllib.request.Request((base or B) + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())
ok = 0; bad = 0
def check(name, cond, info=""):
    global ok, bad
    if cond: ok += 1; print("PASS", name)
    else: bad += 1; print("FAIL", name, info)

# ---------- 样例页面（与 prompts/catalog_extractor/v1/eval/01 的页面文字一致，去掉“工作年限”一句以验证引句核对） ----------
HERE = os.path.dirname(os.path.abspath(__file__))
CASE = os.path.join(HERE, "..", "..", "zt-abroad", "src", "main", "resources", "prompts", "catalog_extractor", "v1", "eval", "01_uk_msc_ielts_tuition.json")
PAGE_TEXT = json.load(open(CASE, encoding="utf-8"))["input"]["pageText"]
WORK = "Applicants are expected to have at least 2 years of relevant work experience."
lines = [l for l in PAGE_TEXT.split("\n") if l != WORK]
body = "".join("<p>" + html.escape(l).replace("£", "&pound;") + "</p>\n" for l in lines[1:])
FIXTURE = ("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + html.escape(lines[0]) + "</title>"
           "<script>window.ielts = 'IELTS 5.5 overall';</script><style>p{margin:0}</style></head><body>"
           "<nav><a href=\"/\">Home</a><a href=\"/study\">Study</a> Work experience: 5 years</nav>"
           "<main>" + body + "</main><footer>Copyright University of Eastvale</footer></body></html>")
SLOW_SECONDS = 17

class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass
    def send(self, status, ctype, data, extra=None):
        self.send_response(status)
        if ctype: self.send_header("Content-Type", ctype)
        for k, v in (extra or {}).items(): self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        try: self.wfile.write(data)
        except Exception: pass
    def do_GET(self):
        p = urllib.parse.urlparse(self.path).path
        if p == "/msc-finance": self.send(200, "text/html; charset=utf-8", FIXTURE.encode("utf-8"))
        elif p == "/go": self.send(302, None, b"", {"Location": "/msc-finance"})
        elif p == "/brochure.pdf": self.send(200, "application/pdf", b"%PDF-1.7 fixture")
        elif p == "/login": self.send(403, "text/html", b"<p>Please sign in</p>")
        elif p == "/big": self.send(200, "text/html", b"<p>" + b"x" * (2 * 1024 * 1024 + 100) + b"</p>")
        elif p == "/slow":
            time.sleep(SLOW_SECONDS); self.send(200, "text/html", FIXTURE.encode("utf-8"))
        else: self.send(404, "text/html", b"<p>Not found</p>")

srv = ThreadingHTTPServer(("127.0.0.1", 0), Fixture)
threading.Thread(target=srv.serve_forever, daemon=True).start()
F = "http://127.0.0.1:%d" % srv.server_address[1]

t = call("POST", "/login", {"username": "admin", "password": "admin123"})["token"]

# ---------- 准备院校、项目 ----------
r = call("POST", "/abroad/school", {"schoolName": "东谷大学（预填测试）", "schoolNameEn": "University of Eastvale (IT)", "country": "UK"}, t)
check("新增院校", r["code"] == 200, r)
sid = call("GET", "/abroad/school/list?country=UK&schoolName=" + urllib.parse.quote("Eastvale (IT)"), None, t)["rows"][0]["schoolId"]
prog = {"schoolId": sid, "programName": "金融学硕士", "programNameEn": "MSc Finance", "degree": "master", "fieldGroup": "business",
        "majorDirection": "finance", "website": F + "/msc-finance"}
r = call("POST", "/abroad/program", prog, t); check("新增项目（无学费、学制）", r["code"] == 200, r)
pid = call("GET", "/abroad/program/list?schoolId=%d" % sid, None, t)["rows"][0]["programId"]
before = call("GET", "/abroad/program/%d" % pid, None, t)["data"]

def prefill(url, program_id=None, token=None, base=None):
    body = {"url": url}
    if program_id is not None: body["programId"] = program_id
    return call("POST", "/abroad/program/aiPrefill", body, token or t, base)

# ---------- 1 预填：found / not_found / discarded ----------
started = time.time()
r = prefill(F + "/msc-finance#entry-requirements", pid)
check("预填成功", r["code"] == 200, r)
d = r.get("data") or {}
fields = {f["fieldName"]: f for f in d.get("fields", [])}
check("返回 19 个字段，含归属与状态", len(fields) == 19 and all(f["target"] in ("program", "requirement") and f["status"] in ("found", "not_found", "discarded") for f in fields.values()), list(fields))
check("返回结构：url（去掉片段）、fetchedAt、elapsedMs、callId", d.get("url") == F + "/msc-finance" and d.get("fetchedAt") and 0 <= d.get("elapsedMs", -1) < 30000 and d.get("callId"), {k: d.get(k) for k in ("url", "fetchedAt", "elapsedMs", "callId")})
found = [k for k, f in fields.items() if f["status"] == "found"]
check("找到的字段", set(found) == {"programNameEn", "degree", "durationMonths", "tuition", "currency", "intakes", "teachingLanguage", "minGpa",
                                "ieltsOverall", "ieltsMinSub", "otherLanguage", "testRequirement", "prerequisites", "prerequisitesRequired", "deadlines"}, found)
check("找到的字段都附原文引句，且引句在页面文字中", all(f["quote"] and " ".join(f["quote"].split()) in " ".join(PAGE_TEXT.split()) for k, f in fields.items() if f["status"] == "found"))
f = fields["ieltsOverall"]
check("雅思总分 7.0 + 原句", f["value"] == 7.0 and f["quote"] == "IELTS 7.0 overall with no less than 6.5 in each component." and f["target"] == "requirement", f)
check("雅思小分 6.5", fields["ieltsMinSub"]["value"] == 6.5, fields["ieltsMinSub"])
check("学费 38500 GBP（实体 &pound; 已解码）", fields["tuition"]["value"] == 38500 and fields["currency"]["value"] == "GBP" and "£38,500" in fields["tuition"]["quote"] and fields["tuition"]["target"] == "program", fields["tuition"])
check("按层次均分（前端形状）", fields["minGpa"]["value"] == {"c9": 80, "985": 80, "211": 80, "double_first_class": 85, "non_key": 85}, fields["minGpa"])
check("其他语言（前端形状，去掉空键）", fields["otherLanguage"]["value"] == {"toefl": {"overall": 100, "minSub": 22}}, fields["otherLanguage"])
check("截止日（前端形状）", fields["deadlines"]["value"] == {"rolling": False, "rounds": [{"name": "Round 1", "date": "2026-11-15"}, {"name": "Round 2", "date": "2027-01-15"}, {"name": "Round 3", "date": "2027-03-01"}]}, fields["deadlines"])
check("入学季数组、学制", fields["intakes"]["value"] == ["fall"] and fields["durationMonths"]["value"] == 12, (fields["intakes"], fields["durationMonths"]))
for k in ("tiersNotAccepted", "testDetail", "acceptCrossMajor"):
    check("not_found：" + k, fields[k]["status"] == "not_found" and fields[k]["value"] is None and fields[k]["quote"] is None and fields[k]["reason"] == "没有找到可靠来源", fields[k])
w = fields["workYears"]
check("discarded：工作年限的引句不在页面中（导航里的 5 years 不算）", w["status"] == "discarded" and w["value"] is None and w["reason"] == "原文中找不到引句，已丢弃", w)
check("summary 计数", d.get("summary") == {"found": 15, "not_found": 3, "discarded": 1}, d.get("summary"))
lg = call("GET", "/abroad/aiCallLog/%d" % d["callId"], None, t)["data"]
check("调用日志：catalog_extractor、业务类型与项目ID", lg["agentCode"] == "catalog_extractor" and lg["status"] == "success" and lg["bizType"] == "program_prefill"
      and lg["bizId"] == str(pid) and lg["promptVersion"] == "v1" and lg["createBy"] == "admin", lg)
check("日志输入中只有页面正文（无 script / nav）", "IELTS 5.5" not in lg["inputSummary"] and "Home" not in lg["inputSummary"][:400], lg["inputSummary"][:300])

# 不写任何业务表
after = call("GET", "/abroad/program/%d" % pid, None, t)["data"]
check("预填不改项目（学费、学制、核对时间不变）", after.get("tuition") is None and after.get("durationMonths") is None and after.get("verifiedTime") is None and after == before, after)
check("预填不新增申请要求 / 字段来源", call("GET", "/abroad/requirement/list?programId=%d" % pid, None, t)["data"] == [])

# ---------- 2 重定向、失败原因 ----------
r = prefill(F + "/go")
check("跟随重定向：url 为最终页面，requestUrl 为原链接", r["code"] == 200 and r["data"]["url"] == F + "/msc-finance" and r["data"]["requestUrl"] == F + "/go", r.get("data", r))
r = prefill(F + "/brochure.pdf", pid)
check("PDF：不是网页", r["code"] == 500 and r["msg"].startswith("不是网页") and "PDF" in r["msg"], r)
r = prefill(F + "/no-such-page", pid)
check("404：页面打不开", r["code"] == 500 and r["msg"].startswith("页面打不开") and "404" in r["msg"], r)
r = prefill(F + "/login", pid)
check("403：需要登录或被拒绝", r["code"] == 500 and "需要登录或被拒绝" in r["msg"], r)
r = prefill(F + "/msc-finance", pid)
check("限流：每个用户每分钟最多 5 次", r["code"] == 500 and "访问过于频繁" in r["msg"], r)
r = call("POST", "/abroad/program/aiPrefill", {"url": ""}, t)
check("链接必填（参数校验）", r["code"] == 500 and "请填写项目官网链接" in r["msg"], r)

wait = 62 - (time.time() - started)
if wait > 0:
    print("... 等待 %d 秒，限流窗口结束" % wait)
    time.sleep(wait)
r = prefill(F + "/big", pid)
check("超过 2 MB：页面太大", r["code"] == 500 and r["msg"].startswith("页面太大"), r)
t0 = time.time()
r = prefill(F + "/slow", pid)
check("页面 15 秒未返回：超时（总时限 30 秒内返回）", r["code"] == 500 and "超时" in r["msg"] and time.time() - t0 < 30, (r, round(time.time() - t0, 1)))
r = prefill("ftp://example.com/msc", pid)
check("非 http(s) 链接被拒", r["code"] == 500 and "只支持 http:// 或 https://" in r["msg"], r)
r = prefill(F + "/msc-finance", 99999999)
check("项目不存在", r["code"] == 500 and "项目不存在" in r["msg"], r)

# ---------- 3 模拟录入者逐字段确认后保存（AI 预填来源 origin=1 + 引句） ----------
url = d["url"]
req = {"programId": pid, "season": "2027-fall", "minGpa": json.dumps(fields["minGpa"]["value"]), "ieltsOverall": fields["ieltsOverall"]["value"],
       "ieltsMinSub": fields["ieltsMinSub"]["value"], "otherLanguage": json.dumps(fields["otherLanguage"]["value"]),
       "testRequirement": fields["testRequirement"]["value"], "prerequisites": fields["prerequisites"]["value"],
       "prerequisitesRequired": fields["prerequisitesRequired"]["value"], "deadlines": json.dumps(fields["deadlines"]["value"]),
       "sources": [{"fieldName": k, "sourceUrl": url, "quote": fields[k]["quote"], "origin": "1"}
                   for k in ("minGpa", "ieltsOverall", "ieltsMinSub", "otherLanguage", "testRequirement", "prerequisites", "deadlines")]}
r = call("POST", "/abroad/requirement", req, t)
check("确认后保存申请要求", r["code"] == 200, r)
saved = call("GET", "/abroad/requirement/list?programId=%d" % pid, None, t)["data"][0]
src = {s["fieldName"]: s for s in saved["sources"]}
check("读回：来源 origin=AI 预填、引句一致、核对人", all(src[k]["origin"] == "1" and src[k]["quote"] == fields[k]["quote"] and src[k]["sourceUrl"] == url
                                         and src[k]["verifiedBy"] == "admin" for k in ("minGpa", "ieltsOverall", "deadlines")), saved["sources"])
check("读回：取值一致", float(saved["ieltsOverall"]) == 7.0 and json.loads(saved["deadlines"]) == fields["deadlines"]["value"] and json.loads(saved["minGpa"]) == fields["minGpa"]["value"], saved)
upd = {**after, "tuition": fields["tuition"]["value"], "currency": fields["currency"]["value"], "durationMonths": fields["durationMonths"]["value"],
       "intakes": ",".join(fields["intakes"]["value"]), "teachingLanguage": fields["teachingLanguage"]["value"]}
r = call("PUT", "/abroad/program", upd, t); check("更新项目基本信息", r["code"] == 200, r)
pr = call("GET", "/abroad/program/%d" % pid, None, t)["data"]
check("项目基本信息读回", pr["tuition"] == 38500 and pr["currency"] == "GBP" and pr["durationMonths"] == 12 and pr["intakes"] == "fall" and pr["teachingLanguage"] == "en", pr)

# ---------- 4 内网地址拦截（allow-private-hosts=false 的实例） ----------
if STRICT:
    ts = call("POST", "/login", {"username": "admin", "password": "admin123"}, base=STRICT)["token"]
    r = prefill(F + "/msc-finance", None, ts, STRICT)
    check("严格实例：本机地址被拦截", r["code"] == 500 and r["msg"].startswith("不能抓取该地址"), r)
    r = prefill("http://169.254.169.254/latest/meta-data/", None, ts, STRICT)
    check("严格实例：云元数据地址被拦截", r["code"] == 500 and r["msg"].startswith("不能抓取该地址"), r)
    r = prefill("http://[::1]:%d/msc-finance" % srv.server_address[1], None, ts, STRICT)
    check("严格实例：IPv6 本机地址被拦截", r["code"] == 500 and r["msg"].startswith("不能抓取该地址"), r)
else:
    print("SKIP 内网地址拦截（未设置 ABR_BASE_STRICT；由单元测试 AbrWebPageFetcherTest 覆盖）")

# ---------- 清理 ----------
r = call("DELETE", "/abroad/program/%d" % pid, None, t); check("删除项目", r["code"] == 200, r)
r = call("DELETE", "/abroad/school/%d" % sid, None, t); check("删除院校", r["code"] == 200, r)
srv.shutdown()
print(f"\n{ok} passed, {bad} failed")
sys.exit(1 if bad else 0)
