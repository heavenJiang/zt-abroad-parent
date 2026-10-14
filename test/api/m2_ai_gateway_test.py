import json, urllib.request, urllib.parse, sys, datetime
import os
B = os.environ.get("ABR_BASE", "http://localhost:8090")
def call(method, path, body=None, token=None):
    req = urllib.request.Request(B + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode())
ok = 0; bad = 0
def check(name, cond, info=""):
    global ok, bad
    if cond: ok += 1; print("PASS", name)
    else: bad += 1; print("FAIL", name, info)

t = call("POST", "/login", {"username": "admin", "password": "admin123"})["token"]
today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).strftime("%Y-%m-%d")

# Agent 配置
al = call("GET", "/abroad/aiAgent/list?pageSize=50", None, t)
codes = {a["agentCode"]: a for a in al["rows"]}
check("种子 Agent 齐全（4 个业务 + _selftest）", {"report_explainer", "resume_parser", "lead_card", "catalog_extractor", "_selftest"} <= set(codes), list(codes))
check("种子审核策略与架构表一致", codes["report_explainer"]["reviewPolicy"] == "direct_sample" and codes["resume_parser"]["reviewPolicy"] == "user_confirm"
      and codes["lead_card"]["reviewPolicy"] == "internal_only" and codes["catalog_extractor"]["reviewPolicy"] == "user_confirm", codes)
check("_selftest 业务停用、提示词完整；业务 Agent 提示词暂缺", codes["_selftest"]["status"] == "1" and codes["_selftest"]["promptAvailable"] is True
      and codes["report_explainer"]["promptAvailable"] is False, codes["_selftest"])
st = call("GET", "/abroad/aiAgent/%d" % codes["_selftest"]["agentId"], None, t)["data"]
orig = dict(st)
check("Agent 详情读回", st["primaryModel"] == "mock:default" and st["promptVersion"] == "v1" and st["timeoutMs"] == 10000, st)
r = call("PUT", "/abroad/aiAgent", {**st, "primaryModel": "nope:x"}, t)
check("未配置的模型被拒", r["code"] == 500 and "未在 abroad.ai.providers" in r["msg"], r)
r = call("PUT", "/abroad/aiAgent", {**st, "reviewPolicy": "whatever"}, t)
check("未知审核策略被拒", r["code"] == 500, r)
r = call("PUT", "/abroad/aiAgent", {**st, "promptVersion": "v9"}, t)
check("切换到不存在的提示词版本被拒", r["code"] == 500 and "不存在" in r["msg"], r)
r = call("PUT", "/abroad/aiAgent", {**st, "agentCode": "renamed"}, t)
check("Agent 编码不可修改", r["code"] == 500, r)
r = call("PUT", "/abroad/aiAgent", {**st, "temperature": 3}, t)
check("温度范围校验", r["code"] == 500, r)

def trial(code, inp, names=None, facts=None):
    body = {"input": inp, "knownNames": names or []}
    if facts is not None: body["facts"] = facts
    return call("POST", "/abroad/aiAgent/test/" + code, body, t)

base = {"studentName": "韩梅梅", "note": "韩梅梅 手机 13812345678，微信：hmm_2026", "profile": {"gpa": 86}}
r = trial("_selftest", base, ["韩梅梅"], [{"text": "UCL MSc CS 雅思 6.5", "source": "https://www.ucl.ac.uk/"}])
d = r["data"]
check("_selftest 试用成功（已停用的自测 Agent 可试用）", r["code"] == 200 and d["status"] == "success", r)
check("输出已还原姓名", d["output"]["summary"] == "韩梅梅的自测摘要：网关链路正常。", d)
check("成功调用带 token、费用、模型", d["promptTokens"] > 0 and d["completionTokens"] > 0 and d["cost"] > 0 and d["modelUsed"] == "mock:default" and not d["fallbackUsed"], d)
log = call("GET", "/abroad/aiCallLog/%d" % d["callId"], None, t)["data"]
check("调用日志逐字段", log["status"] == "success" and log["promptVersion"] == "v1" and len(log["promptHash"]) == 64 and log["bizType"] == "trial"
      and log["createBy"] == "admin" and log["promptTokens"] == d["promptTokens"] and abs(log["cost"] - d["cost"]) < 1e-9 and log["fallbackUsed"] == "N" and log["attempts"] == 1, log)
check("日志输入、输出已去标识化", "[姓名1]" in log["inputSummary"] and "[手机1]" in log["inputSummary"] and "[微信1]" in log["inputSummary"]
      and "韩梅梅" not in log["inputSummary"] + log["output"] and "13812345678" not in log["inputSummary"] and "hmm_2026" not in log["inputSummary"], log)

r = trial("_selftest", {**base, "_mock": "timeout"}, ["韩梅梅"])["data"]
check("主模型超时 → 备用模型", r["status"] == "success" and r["fallbackUsed"] and r["attempts"] == 2, r)
check("切换备用记入日志", call("GET", "/abroad/aiCallLog/%d" % r["callId"], None, t)["data"]["fallbackUsed"] == "Y")
r = trial("_selftest", {**base, "_mock": "error_all"})["data"]
check("主备均不可用 → error 并记日志", r["status"] == "error" and "主备模型均不可用" in r["message"]
      and call("GET", "/abroad/aiCallLog/%d" % r["callId"], None, t)["data"]["status"] == "error", r)
r = trial("_selftest", {**base, "_mock": "schema_fail_once"})["data"]
check("Schema 失败一次 → 重试成功", r["status"] == "success" and r["attempts"] == 2, r)
r = trial("_selftest", {**base, "_mock": "invalid_json"})["data"]
check("两次非法输出 → degraded", r["status"] == "degraded" and r["output"] is None and "兜底" in r["message"], r)
r = trial("_selftest", {**base, "_mock": "banned"})["data"]
check("违禁内容 → filtered，不返回输出", r["status"] == "filtered" and r["output"] is None and r["flags"][0]["word"] == "保证录取", r)
r = trial("report_explainer", base)
check("提示词目录缺失 → 干净的 error", r["code"] == 200 and r["data"]["status"] == "error" and "提示词目录" in r["data"]["message"], r)
check("提示词缺失也记日志", call("GET", "/abroad/aiCallLog/%d" % r["data"]["callId"], None, t)["data"]["status"] == "error")
r = trial("no_such_agent", base)["data"]
check("不存在的 Agent → error", r["status"] == "error" and "不存在" in r["message"], r)

# 审核策略：顾问确认后发出
r = call("PUT", "/abroad/aiAgent", {**orig, "reviewPolicy": "review_before_send"}, t)
check("修改审核策略为顾问确认后发出", r["code"] == 200, r)
check("修改读回", call("GET", "/abroad/aiAgent/%d" % orig["agentId"], None, t)["data"]["reviewPolicy"] == "review_before_send")
r = trial("_selftest", base, ["韩梅梅"])["data"]
check("review_before_send → pending_review，内容不返回", r["status"] == "pending_review" and r["output"] is None and r["reviewId"], r)
rid = r["reviewId"]
rv = call("GET", "/abroad/aiReview/%d" % rid, None, t)["data"]
check("审核记录内容已还原、状态 pending", rv["status"] == "pending" and json.loads(rv["content"])["summary"].startswith("韩梅梅") and rv["callId"] == r["callId"] and rv["bizType"] == "trial", rv)
check("调用日志关联审核ID", call("GET", "/abroad/aiCallLog/%d" % r["callId"], None, t)["data"]["reviewId"] == rid)
rl = call("GET", "/abroad/aiReview/list?status=pending&agentCode=_selftest", None, t)
check("审核列表按状态筛选", any(x["reviewId"] == rid for x in rl["rows"]), rl)
r = call("PUT", "/abroad/aiReview/reject/%d" % rid, {"reason": ""}, t)
check("驳回必须填原因", r["code"] == 500, r)
r = call("PUT", "/abroad/aiReview/approve/%d" % rid, None, t)
check("审核通过返回最终内容", r["code"] == 200 and r["data"]["summary"].startswith("韩梅梅"), r)
rv = call("GET", "/abroad/aiReview/%d" % rid, None, t)["data"]
check("通过后读回：状态、审核人、时间", rv["status"] == "approved" and rv["reviewer"] == "admin" and rv["reviewTime"], rv)
r = call("PUT", "/abroad/aiReview/approve/%d" % rid, None, t)
check("已处理不能重复审核", r["code"] == 500, r)

r = trial("_selftest", {**base, "_mock": "banned"}, ["韩梅梅"])["data"]
check("review_before_send + 违禁词 → 带命中原因进入审核", r["status"] == "pending_review" and r["reviewId"], r)
rid2 = r["reviewId"]
rv = call("GET", "/abroad/aiReview/%d" % rid2, None, t)["data"]
check("审核记录带过滤命中项", json.loads(rv["flags"])[0]["word"] == "保证录取", rv)
r = call("PUT", "/abroad/aiReview/edit/%d" % rid2, {"editedContent": json.dumps({"summary": "x", "level": "bad", "score": 1, "tags": []})}, t)
check("修改内容不符合 Schema 被拒", r["code"] == 500 and "level" in r["msg"], r)
r = call("PUT", "/abroad/aiReview/edit/%d" % rid2, {"editedContent": "{bad"}, t)
check("修改内容非法 JSON 被拒", r["code"] == 500, r)
edited = {"summary": "韩梅梅的自测摘要（顾问已修改）", "level": "warn", "score": 70, "tags": ["selftest"], "contact": None}
r = call("PUT", "/abroad/aiReview/edit/%d" % rid2, {"editedContent": json.dumps(edited, ensure_ascii=False), "reason": "去掉录取承诺"}, t)
check("修改后通过返回修改后的内容", r["code"] == 200 and r["data"] == edited, r)
rv = call("GET", "/abroad/aiReview/%d" % rid2, None, t)["data"]
check("修改后读回", rv["status"] == "edited" and json.loads(rv["editedContent"]) == edited and rv["reason"] == "去掉录取承诺", rv)

r = trial("_selftest", base)["data"]
r = call("PUT", "/abroad/aiReview/reject/%d" % r["reviewId"], {"reason": "内容不准确"}, t)
check("驳回", r["code"] == 200, r)

# 抽检
r = call("PUT", "/abroad/aiAgent", {**orig, "reviewPolicy": "direct_sample", "sampleRate": 0}, t)
check("抽检比例为 0 被拒", r["code"] == 500, r)
call("PUT", "/abroad/aiAgent", {**orig, "reviewPolicy": "direct_sample", "sampleRate": 1}, t)
r = trial("_selftest", base)["data"]
check("direct_sample（比例 1）→ 返回内容并生成抽检记录", r["status"] == "success" and r["output"] and r["reviewId"]
      and call("GET", "/abroad/aiReview/%d" % r["reviewId"], None, t)["data"]["status"] == "sampled", r)

# 恢复 _selftest
r = call("PUT", "/abroad/aiAgent", orig, t)
check("恢复 _selftest 配置", r["code"] == 200 and call("GET", "/abroad/aiAgent/%d" % orig["agentId"], None, t)["data"]["reviewPolicy"] == "direct")

# 日志列表与统计
ll = call("GET", "/abroad/aiCallLog/list?agentCode=_selftest&status=success&params%5BbeginTime%5D=" + today + "&params%5BendTime%5D=" + today, None, t)
check("日志按 Agent、状态、时间筛选", ll["total"] >= 4 and all(x["agentCode"] == "_selftest" and x["status"] == "success" for x in ll["rows"]), ll["total"])
check("日志列表不含输入输出正文", "output" not in ll["rows"][0] or ll["rows"][0]["output"] is None, ll["rows"][0])
check("时间筛选（明天无数据）", call("GET", "/abroad/aiCallLog/list?params%5BbeginTime%5D=2099-01-01", None, t)["total"] == 0)
check("日志按状态筛选 error", call("GET", "/abroad/aiCallLog/list?status=error", None, t)["total"] >= 3)
s = call("GET", "/abroad/aiCallLog/stats?beginTime=" + today + "&endTime=" + today, None, t)["data"]
tot = s["total"]
check("统计：今天有调用、token、费用", tot["calls"] >= 12 and tot["promptTokens"] > 0 and tot["cost"] > 0 and s["daily"][0]["day"] == today, s)
check("统计：按 Agent 汇总与总计一致", sum(a["calls"] for a in s["agents"]) == tot["calls"] and any(a["agentCode"] == "_selftest" for a in s["agents"]), s["agents"])
s2 = call("GET", "/abroad/aiCallLog/stats", None, t)["data"]
check("统计默认最近 7 天", s2["endTime"] == today and s2["total"]["calls"] == tot["calls"], s2)
check("统计日期格式校验", call("GET", "/abroad/aiCallLog/stats?beginTime=2026/10/01", None, t)["code"] == 500)
check("统计开始晚于结束被拒", call("GET", "/abroad/aiCallLog/stats?beginTime=2026-10-10&endTime=2026-10-01", None, t)["code"] == 500)
print(f"\n{ok} passed, {bad} failed")
sys.exit(1 if bad else 0)
