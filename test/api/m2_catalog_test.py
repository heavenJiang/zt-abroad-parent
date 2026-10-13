import json, urllib.request, sys
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
r = call("POST", "/abroad/school", {"schoolName": "曼彻斯特大学", "schoolNameEn": "The University of Manchester", "country": "UK", "city": "Manchester", "rankings": '[{"list":"QS","year":2026,"rank":35}]'}, t)
check("新增院校", r["code"] == 200, r)
r = call("POST", "/abroad/school", {"schoolName": "曼大", "schoolNameEn": "The University of Manchester", "country": "UK"}, t)
check("院校同国家英文名重复被拒", r["code"] == 500, r)
r = call("POST", "/abroad/school", {"schoolName": "x", "schoolNameEn": "y", "country": "UK", "rankings": "{bad"}, t)
check("榜单非法 JSON 被拒", r["code"] == 500 and "JSON" in r["msg"], r)
lst = call("GET", "/abroad/school/list?country=UK&schoolName=manchester", None, t)
check("院校按国家、英文名模糊筛选", lst["total"] == 1, lst)
sid = lst["rows"][0]["schoolId"]
check("院校读回榜单", json.loads(lst["rows"][0]["rankings"])[0]["rank"] == 35, lst["rows"][0])

p = {"schoolId": sid, "programName": "金融学硕士", "programNameEn": "MSc Finance", "degree": "master", "fieldGroup": "business",
     "majorDirection": "finance", "durationMonths": 12, "intakes": "fall", "tuition": 38000.5, "currency": "GBP", "difficulty": "D2"}
r = call("POST", "/abroad/program", p, t); check("新增项目", r["code"] == 200, r)
r = call("POST", "/abroad/program", p, t); check("同院校同名同学位项目被拒", r["code"] == 500, r)
r = call("POST", "/abroad/program", {**p, "programNameEn": ""}, t); check("项目英文名必填校验", r["code"] == 500, r)
pl = call("GET", "/abroad/program/list?country=UK&degree=master&fieldGroup=business", None, t)
check("项目按国家、学位、学科大类筛选", pl["total"] == 1, pl)
pid = pl["rows"][0]["programId"]
pr = call("GET", f"/abroad/program/{pid}", None, t)["data"]
check("项目读回逐字段", pr["schoolName"] == "曼彻斯特大学" and pr["tuition"] == 38000.5 and pr["durationMonths"] == 12 and pr["verifiedTime"] is None, pr)
pl2 = call("GET", "/abroad/program/list?params%5BverifyExpired%5D=Y", None, t)
check("未核对项目出现在“超过90天未核对”筛选", pl2["total"] == 1, pl2)
check("项目分页条件筛选（学科不匹配）", call("GET", "/abroad/program/list?fieldGroup=stem", None, t)["total"] == 0)

req = {"programId": pid, "season": "2027-fall", "minGpa": '{"985": 80, "211": 85, "non_key": 88}', "ieltsOverall": 6.5, "ieltsMinSub": 6.0,
       "deadlines": '{"rolling": true}', "testRequirement": "none"}
r = call("POST", "/abroad/requirement", req, t)
check("关键字段无来源被拒", r["code"] == 500 and "来源" in r["msg"], r)
src = [{"fieldName": f, "sourceUrl": "https://www.manchester.ac.uk/msc-finance/", "quote": "We require ..."} for f in ["minGpa", "ieltsOverall", "ieltsMinSub", "deadlines"]]
r = call("POST", "/abroad/requirement", {**req, "sources": src[:3]}, t)
check("截止日缺来源被拒", r["code"] == 500 and "截止日" in r["msg"], r)
r = call("POST", "/abroad/requirement", {**req, "minGpa": '{"abc": 80}', "sources": src}, t)
check("未知本科层次被拒", r["code"] == 500, r)
r = call("POST", "/abroad/requirement", {**req, "ieltsMinSub": 7.0, "sources": src}, t)
check("小分高于总分被拒", r["code"] == 500, r)
r = call("POST", "/abroad/requirement", {**req, "season": "2027fall", "sources": src}, t)
check("申请季格式校验", r["code"] == 500, r)
r = call("POST", "/abroad/requirement", {**req, "sources": [{**s, "sourceUrl": "ftp://x"} for s in src]}, t)
check("来源链接必须 http(s)", r["code"] == 500, r)
r = call("POST", "/abroad/requirement", {**req, "sources": src}, t)
check("带来源新增申请要求", r["code"] == 200, r)
r = call("POST", "/abroad/requirement", {**req, "sources": src}, t)
check("同项目同申请季重复被拒", r["code"] == 500, r)
rl = call("GET", f"/abroad/requirement/list?programId={pid}", None, t)["data"]
rq = rl[0]
check("申请要求读回", len(rl) == 1 and json.loads(rq["minGpa"]) == {"985": 80, "211": 85, "non_key": 88} and rq["ieltsOverall"] == 6.5 and json.loads(rq["deadlines"]) == {"rolling": True}, rq)
check("来源读回 4 条且记核对人", len(rq["sources"]) == 4 and all(s["verifiedBy"] == "admin" and s["verifiedTime"] for s in rq["sources"]), rq["sources"])
pr = call("GET", f"/abroad/program/{pid}", None, t)["data"]
check("保存来源后项目更新核对时间", pr["verifiedTime"] is not None and pr["verifiedBy"] == "admin", pr)
check("已核对项目不在超期筛选中", call("GET", "/abroad/program/list?params%5BverifyExpired%5D=Y", None, t)["total"] == 0)

upd = {**rq, "ieltsMinSub": None, "deadlines": None, "prerequisites": "微积分、统计", "sources": [s for s in rq["sources"] if s["fieldName"] in ("minGpa", "ieltsOverall")]}
r = call("PUT", "/abroad/requirement", upd, t); check("修改申请要求（清空小分与截止日）", r["code"] == 200, r)
rq2 = call("GET", f"/abroad/requirement/{rq['requirementId']}", None, t)["data"]
check("清空字段读回为空，来源同步删除", rq2["ieltsMinSub"] is None and rq2["deadlines"] is None and rq2["prerequisites"] == "微积分、统计" and sorted(s["fieldName"] for s in rq2["sources"]) == ["ieltsOverall", "minGpa"], rq2)

r = call("DELETE", f"/abroad/school/{sid}", None, t); check("有项目的院校不能删除", r["code"] == 500, r)

for kw, expect in [("北京大学", "北京大学"), ("北大", "北京大学"), ("华科", "华中科技大学"), ("哈工大", "哈尔滨工业大学"), ("西南财经", "西南财经大学")]:
    s = call("GET", "/abroad/cnUniversity/search?keyword=" + urllib.parse.quote(kw), None, t)["data"]
    check(f"高校搜索 {kw} → {expect}", s and s[0]["universityName"] == expect, [x["universityName"] for x in s][:3])
s = call("GET", "/abroad/cnUniversity/search?keyword=" + urllib.parse.quote("%"), None, t)["data"]
check("搜索 1 个字符或通配符返回空", s == [], s)
s = call("GET", "/abroad/cnUniversity/search?keyword=" + urllib.parse.quote("某某学院"), None, t)["data"]
check("搜不到返回空（按双非处理）", s == [], s)
cl = call("GET", "/abroad/cnUniversity/list?tier=c9&pageSize=50", None, t)
check("国内高校按层次筛选（C9 含分校区）", cl["total"] == 11, cl["total"])
r = call("POST", "/abroad/cnUniversity", {"universityName": "测试大学", "tier": "non_key"}, t)
check("双非不允许录入", r["code"] == 500, r)

r = call("DELETE", f"/abroad/program/{pid}", None, t); check("删除项目", r["code"] == 200, r)
check("删除项目后申请要求一并删除", call("GET", f"/abroad/requirement/list?programId={pid}", None, t)["data"] == [])
r = call("DELETE", f"/abroad/school/{sid}", None, t); check("项目删完后可删院校", r["code"] == 200, r)
print(f"\n{ok} passed, {bad} failed")
sys.exit(1 if bad else 0)
