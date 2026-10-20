#!/usr/bin/env bash
# ----------------------------------------------------------------------------
# M10-03 熔断演练：模拟退订激增、投诉、平台违规提示，验证自动暂停、熔断记录、员工通知与暂停后的发送拦截，最后恢复原状。
#
# 只在测试环境运行：要求消息渠道为 mock（ABR_CHANNEL_MOCK=true）、服务号登录为 mock（ABR_WECHAT_OA_MOCK=true），否则拒绝执行。
# 用法：BASE=http://测试环境后端:8090 APP_URL=https://测试环境C端域名/ TOKEN=<管理员令牌（浏览器登录后复制 Admin-Token）> deploy/drill/breaker-drill.sh
# 依赖：curl、python3。演练会留下演练用的 C 端账号、访客、触达与信号记录（便于复盘），演练模板与演练账号在结束时删除。
# ----------------------------------------------------------------------------
set -u
BASE="${BASE:-http://localhost:8090}"
# C 端地址（服务号授权回跳须在 C 端域名内，与后端 abroad.app.url 一致）
APP_URL="${APP_URL:-http://localhost:5173/}"
TOKEN="${TOKEN:?请设置 TOKEN（管理员令牌）}"
UNSUB_AGENT="${UNSUB_AGENT:-booking_agent}"
COMPLAINT_AGENT="${COMPLAINT_AGENT:-nurture_agent}"
USERS="${USERS:-14}"
WAIT="${WAIT:-180}"
REPORT="${REPORT:-breaker-drill-$(date +%Y%m%d-%H%M%S).md}"
PASS=0; FAIL=0
LINES=()

api() { # api METHOD PATH [JSON]
  if [ -n "${3:-}" ]; then curl -s -X "$1" "${BASE}$2" -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' -d "$3"
  else curl -s -X "$1" "${BASE}$2" -H "Authorization: Bearer ${TOKEN}"; fi
}
py() { python3 -c "$1"; }
jget() { py "import json,sys;d=json.load(sys.stdin);print($1)"; }
log() { echo "$*"; LINES+=("$*"); }
check() { # check 结果(0/1) 说明
  if [ "$1" = "1" ] || [ "$1" = "True" ]; then PASS=$((PASS+1)); log "- ✅ $2"; else FAIL=$((FAIL+1)); log "- ❌ $2"; fi
}
wait_for() { # wait_for 描述 python 表达式（读 stdin 为 api 结果）路径
  local desc="$1" expr="$2" path="$3" t=0
  while [ ${t} -lt "${WAIT}" ]; do
    # 暂停与熔断记录、员工提醒在同一轮评估中先后写入，看到暂停后稍等再核对其余结果
    if [ "$(api GET "${path}" | jget "${expr}" 2>/dev/null)" = "True" ]; then sleep 5; echo 1; return; fi
    sleep 10; t=$((t+10))
  done
  echo 0
}
agent_field() { api GET "/abroad/orchestrator/agent/$1" | jget "d['data']['$2']"; }

log "# 熔断演练记录"
log ""
log "- 时间：$(date '+%Y-%m-%d %H:%M:%S')"
log "- 环境：${BASE}"
log ""

# 0、前置检查
status=$(api GET /abroad/message/channel/status)
if [ "$(echo "${status}" | jget "all(c['mock'] for c in d['data'])")" != "True" ]; then
  echo "消息渠道不是 mock 模式，拒绝在可能真实发送的环境中演练"; exit 2
fi
if [ "$(api GET /abroad/orchestrator/setting | jget "d['data']['paused']")" != "N" ]; then
  echo "获客自动化当前是暂停状态，请先确认原因并恢复后再演练"; exit 2
fi
for a in "${UNSUB_AGENT}" "${COMPLAINT_AGENT}"; do
  if [ "$(agent_field "${a}" paused)" != "N" ]; then echo "${a} 当前已暂停，请先恢复后再演练"; exit 2; fi
done
since=$(date '+%Y-%m-%d %H:%M:%S')
tpl=$(api POST /abroad/message/template '{"templateCode":"drill_marketing","channel":"service_account","templateName":"熔断演练","category":"marketing","platformTemplateId":"DRILL","fieldMap":"{\"thing1\":\"{x}\"}","content":"演练消息：{x}","status":"0","remark":"M10-03 熔断演练，结束时删除"}')
TPL=$(api GET "/abroad/message/template/list?templateCode=drill_marketing" | jget "[t['templateId'] for t in d['rows'] if t['templateCode']=='drill_marketing'][0]")
log "## 1、退订激增（${UNSUB_AGENT}：规则 agent_unsubscribe_rate，24 小时退订率 ≥ 5%，至少 40 条营销触达）"
VTS=(); IDS=()
for i in $(seq 1 "${USERS}"); do
  vt=$(curl -s -X POST "${BASE}/app/visitor" -H 'Content-Type: application/json' -d '{"channelCode":"drill"}' | jget "d['visitorToken']")
  r=$(curl -s "${BASE}/app/auth/wechat/url?redirect=${APP_URL}" -H "X-Visitor-Token: ${vt}")
  code=$(echo "${r}" | py "import json,sys,re;d=json.load(sys.stdin)['data'];print(re.search(r'code=([^&]+)',d['url']).group(1))")
  state=$(echo "${r}" | jget "d['data']['state']")
  at=$(curl -s -X POST "${BASE}/app/auth/wechat" -H 'Content-Type: application/json' -H "X-Visitor-Token: ${vt}" -d "{\"code\":\"${code}\",\"state\":\"${state}\"}" | jget "d['data']['appToken']")
  id=$(api GET "/abroad/appUser/list?pageNum=1&pageSize=1" | jget "d['rows'][0]['appUserId']")
  # 登录后访客令牌并入账号而失效，之后的 C 端操作带学生令牌
  VTS+=("${at}"); IDS+=("${id}")
  # C 端登录按 IP 限流（每分钟 10 次），演练放慢节奏
  sleep 7
done
sent=0
for id in "${IDS[@]}"; do
  for k in 1 2 3; do
    s=$(api POST /abroad/message/test "{\"templateId\":${TPL},\"targetType\":\"app_user\",\"targetId\":${id},\"agentCode\":\"${UNSUB_AGENT}\",\"vars\":{\"x\":\"第 ${k} 条\"}}" | jget "d['data']['status']")
    [ "${s}" = "mock" ] && sent=$((sent+1))
  done
done
check "$([ ${sent} -ge 40 ] && echo 1 || echo 0)" "向 ${USERS} 个演练账号发出营销消息 ${sent} 条（每人 3 条，频控上限内）"
# 需要的退订数：按 24 小时内该 Agent 已发出的营销触达与规则阈值计算（重复演练时窗口内已有更多触达）
total=$(api GET "/abroad/orchestrator/touch/list?pageNum=1&pageSize=1&agentCode=${UNSUB_AGENT}&category=marketing&status=sent&params%5BbeginTime%5D=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d yesterday +%Y-%m-%d)" | jget "d['total']")
threshold=$(api GET "/abroad/orchestrator/breaker/list?pageSize=50" | jget "[float(r['threshold']) for r in d['rows'] if r['ruleCode']=='agent_unsubscribe_rate'][0]")
need=$(py "import math;print(min(${USERS}, math.floor(${threshold} * ${total}) + 1))")
log "- 窗口内营销触达约 ${total} 条，阈值 ${threshold}，退订 ${need} 人"
for n in $(seq 0 $((need-1))); do
  curl -s -X PUT "${BASE}/app/subscription" -H 'Content-Type: application/json' -H "X-App-Token: ${VTS[${n}]}" -d '{"marketing":false}' >/dev/null
done
log "- ${need} 个演练账号在 C 端退订营销消息"
check "$(wait_for x "d['data']['paused']=='Y' and d['data']['pausedBy']=='breaker'" "/abroad/orchestrator/agent/${UNSUB_AGENT}")" "${UNSUB_AGENT} 被熔断自动暂停"
check "$(api GET "/abroad/orchestrator/breaker/trip/list?pageSize=50" | jget "any(t['ruleCode']=='agent_unsubscribe_rate' and t['agentCode']=='${UNSUB_AGENT}' for t in d['rows'])")" "熔断记录：agent_unsubscribe_rate"
check "$(api GET "/abroad/leadAssign/notice?unread=true" | jget "any(n['noticeKind']=='breaker_tripped' and '退订' in n['title'] for n in d['data']) or any(n['noticeKind']=='breaker_tripped' for n in d['data'])")" "有暂停权限的员工收到熔断提醒"
s=$(api POST /abroad/message/test "{\"templateId\":${TPL},\"targetType\":\"app_user\",\"targetId\":${IDS[5]},\"agentCode\":\"${UNSUB_AGENT}\",\"vars\":{\"x\":\"暂停后\"}}" | jget "d['data']['status']+'|'+d['data']['reason']")
check "$([[ "${s}" == blocked*暂停* ]] && echo 1 || echo 0)" "暂停后再发被拦下（${s}）"
api PUT "/abroad/orchestrator/agent/resume/${UNSUB_AGENT}" >/dev/null
s=$(api POST /abroad/message/test "{\"templateId\":${TPL},\"targetType\":\"app_user\",\"targetId\":${IDS[0]},\"agentCode\":\"${UNSUB_AGENT}\",\"vars\":{\"x\":\"恢复后\"}}" | jget "d['data']['status']+'|'+d['data']['reason']")
check "$([[ "${s}" == blocked*退订* ]] && echo 1 || echo 0)" "恢复后，在 C 端退订的人（按账号发送）仍被拦下（${s}）"
log ""

log "## 2、投诉（${COMPLAINT_AGENT}：规则 agent_complaint，24 小时投诉 ≥ 2）"
for k in 1 2; do api POST /abroad/orchestrator/breaker/signal "{\"signalType\":\"complaint\",\"agentCode\":\"${COMPLAINT_AGENT}\",\"detail\":\"{\\\"note\\\":\\\"熔断演练投诉 ${k}\\\"}\",\"source\":\"manual\"}" >/dev/null; done
check "$(wait_for x "d['data']['paused']=='Y' and d['data']['pausedBy']=='breaker'" "/abroad/orchestrator/agent/${COMPLAINT_AGENT}")" "${COMPLAINT_AGENT} 被熔断自动暂停"
check "$(api GET "/abroad/orchestrator/breaker/trip/list?pageSize=50" | jget "any(t['ruleCode']=='agent_complaint' and t['agentCode']=='${COMPLAINT_AGENT}' for t in d['rows'])")" "熔断记录：agent_complaint"
log ""

log "## 3、平台违规提示（规则 account_violation 暂停账号、global_violation 全局暂停，24 小时 ≥ 1）"
api POST /abroad/content/account '{"accountCode":"drill_acc","accountName":"熔断演练账号","platform":"xiaohongshu","ownerUserId":1,"persona":"studio","publishMode":"package","authStatus":"none","dailyPublishCap":1,"status":"0","remark":"M10-03 演练，结束时删除"}' >/dev/null
ACC=$(api GET "/abroad/content/account/list?pageSize=100" | jget "[a['accountId'] for a in d['rows'] if a['accountCode']=='drill_acc'][0]")
api POST /abroad/orchestrator/breaker/signal '{"signalType":"platform_violation","accountCode":"drill_acc","detail":"{\"note\":\"熔断演练：平台违规提示\"}","source":"manual"}' >/dev/null
check "$(wait_for x "d['data']['paused']=='Y'" "/abroad/orchestrator/setting")" "全部获客自动化被熔断全局暂停"
check "$(api GET "/abroad/content/account/${ACC}" | jget "d['data']['paused']=='Y'")" "演练账号被暂停"
s=$(api POST /abroad/message/test "{\"templateId\":${TPL},\"targetType\":\"app_user\",\"targetId\":${IDS[6]},\"agentCode\":\"${UNSUB_AGENT}\",\"vars\":{\"x\":\"全局暂停后\"}}" | jget "d['data']['status']+'|'+d['data']['reason']")
check "$([[ "${s}" == blocked*全局暂停* ]] && echo 1 || echo 0)" "全局暂停后任何 Agent 的发送被拦下（${s}）"
log ""

# 4、恢复
api PUT /abroad/orchestrator/setting/resume >/dev/null
for a in "${UNSUB_AGENT}" "${COMPLAINT_AGENT}"; do api PUT "/abroad/orchestrator/agent/resume/${a}" >/dev/null; done
api PUT "/abroad/content/account/resume/${ACC}" >/dev/null
api DELETE "/abroad/content/account/${ACC}" >/dev/null
api DELETE "/abroad/message/template/${TPL}" >/dev/null
ok=$(api GET /abroad/orchestrator/setting | jget "d['data']['paused']=='N'")
for a in "${UNSUB_AGENT}" "${COMPLAINT_AGENT}"; do [ "$(agent_field "${a}" paused)" = "N" ] || ok=False; done
check "$([ "${ok}" = "True" ] && echo 1 || echo 0)" "已恢复：全局与 ${UNSUB_AGENT}、${COMPLAINT_AGENT} 均为运行中；演练模板与演练账号已删除"
log ""
log "结果：通过 ${PASS} 项，失败 ${FAIL} 项。演练留下 ${USERS} 个演练 C 端账号（渠道码 drill）及其触达、退订与信号记录（${since} 之后），便于复盘。"
printf '%s\n' "${LINES[@]}" > "${REPORT}"
echo "报告：${REPORT}"
[ ${FAIL} -eq 0 ]
