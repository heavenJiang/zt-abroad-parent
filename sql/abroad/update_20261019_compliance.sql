-- ----------------------------------------------------------------------------
-- M6-03 合规审查 compliance_checker：合规词表（词 / 例外短语 / 正则，按平台生效）
--
-- 合规闸门在所有对外内容和营销消息发出前执行（确定性规则，不调用大模型）：
-- 词表与正则（广告违禁词、录取承诺、教育广告禁止情形、需核实的说法、站外导流、诱导分享、个人信息）
-- + 事实核对（数字与引用的院校库记录比对、提到的院校须被引用、项目 90 天内核对过）+ AI 生成标识。
-- 词表种子来自 M0-10 草案（seed/compliance/，待创始人确认、律师审阅），在管理端“获客运营 → 合规词表”修改。
-- 依赖：update_20261018_orchestrator.sql（获客运营菜单）、update_20261019_publish_content.sql（内容与平台字典）
-- 可重复执行
-- ----------------------------------------------------------------------------

-- ----------------------------
-- 1、合规词表
-- ----------------------------
create table if not exists abr_compliance_rule (
  rule_id      bigserial      not null,
  category     varchar(32)    not null,
  match_type   varchar(16)    not null default 'word',
  pattern      varchar(200)   not null,
  action       varchar(16)    not null default 'block',
  platforms    jsonb          not null default '[]'::jsonb,
  status       char(1)        default '0',
  create_by    varchar(64)    default '',
  create_time  timestamp,
  update_by    varchar(64)    default '',
  update_time  timestamp,
  remark       varchar(500)   default null,
  primary key (rule_id)
);
create unique index if not exists uk_abr_compliance_rule on abr_compliance_rule (category, match_type, lower(pattern));

comment on table  abr_compliance_rule             is '合规词表（compliance_checker 在对外内容和营销消息发出前检查；词忽略大小写与空白匹配）';
comment on column abr_compliance_rule.rule_id     is '规则ID';
comment on column abr_compliance_rule.category    is '类别（字典 abr_compliance_category）';
comment on column abr_compliance_rule.match_type  is '匹配方式（字典 abr_compliance_match：word 词 / exception 例外短语，同类别内落在其中的命中不算 / regex 正则）';
comment on column abr_compliance_rule.pattern     is '词、例外短语或正则';
comment on column abr_compliance_rule.action      is '动作（字典 abr_compliance_action：block 拦截 / cite 引用依据中有案例、offer 或网页来源时放行，否则拦截；例外短语不用）';
comment on column abr_compliance_rule.platforms   is '生效平台（字典 abr_platform 的值数组；空数组为全部平台与消息）';
comment on column abr_compliance_rule.status      is '状态（0正常 1停用）';
comment on column abr_compliance_rule.create_by   is '创建者';
comment on column abr_compliance_rule.create_time is '创建时间';
comment on column abr_compliance_rule.update_by   is '更新者';
comment on column abr_compliance_rule.update_time is '更新时间';
comment on column abr_compliance_rule.remark      is '备注';

-- ----------------------------
-- 2、字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select v.dict_name, v.dict_type, '0', 'admin', now(), v.remark
from (values
  ('合规类别',     'abr_compliance_category', '合规词表与闸门结果的类别（fact / citation / ai_label 为系统检查，不在词表中配置）'),
  ('合规匹配方式', 'abr_compliance_match',    '合规词表的匹配方式'),
  ('合规动作',     'abr_compliance_action',   '合规词表命中后的动作')
) as v(dict_name, dict_type, remark)
where not exists (select 1 from sys_dict_type t where t.dict_type = v.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, v.dict_type, v.list_class, 'N', '0', 'admin', now(), v.remark
from (values
  (1,  '广告违禁词',       'ad_banned',         'abr_compliance_category', 'danger',  '《广告法》第九条'),
  (2,  '录取承诺',         'admission_promise', 'abr_compliance_category', 'danger',  '《广告法》第二十四条第（一）项'),
  (3,  '教育广告禁止情形', 'education_ad',      'abr_compliance_category', 'danger',  '《广告法》第二十四条第（二）（三）项'),
  (4,  '需核实的说法',     'fact_claim',        'abr_compliance_category', 'warning', '《广告法》第二十八条'),
  (5,  '站外导流',         'diversion',         'abr_compliance_category', 'warning', '各平台社区规范'),
  (6,  '诱导分享与关注',   'inducement',        'abr_compliance_category', 'warning', '微信外部链接内容管理规范'),
  (7,  '个人信息',         'privacy',           'abr_compliance_category', 'danger',  ''),
  (8,  '事实核对',         'fact',              'abr_compliance_category', 'primary', '系统检查'),
  (9,  '引用依据',         'citation',          'abr_compliance_category', 'primary', '系统检查'),
  (10, 'AI 生成标识',      'ai_label',          'abr_compliance_category', 'primary', '系统检查'),
  (1,  '词',       'word',      'abr_compliance_match', 'primary', '忽略大小写与空白'),
  (2,  '例外短语', 'exception', 'abr_compliance_match', 'info',    '同类别内落在其中的命中不算'),
  (3,  '正则',     'regex',     'abr_compliance_match', 'warning', '忽略大小写'),
  (1,  '拦截',         'block', 'abr_compliance_action', 'danger',  ''),
  (2,  '须有引用依据', 'cite',  'abr_compliance_action', 'warning', '引用依据中有案例、offer 或网页来源时放行')
) as v(dict_sort, dict_label, dict_value, dict_type, list_class, remark)
where not exists (select 1 from sys_dict_data d where d.dict_type = v.dict_type and d.dict_value = v.dict_value);

-- ----------------------------
-- 3、词表种子（M0-10 草案；seed/compliance/*.txt）
-- ----------------------------
insert into abr_compliance_rule (category, match_type, pattern, action, platforms, status, create_by, create_time, remark)
select v.category, v.match_type, v.pattern, v.action, cast(v.platforms as jsonb), '0', 'admin', now(),
       case when v.remark = '' then 'M0-10 草案' else v.remark || '（M0-10 草案）' end
from (values
  ('ad_banned', 'word', '最好', 'block', '[]', ''),
  ('ad_banned', 'word', '最佳', 'block', '[]', ''),
  ('ad_banned', 'word', '最优', 'block', '[]', ''),
  ('ad_banned', 'word', '最强', 'block', '[]', ''),
  ('ad_banned', 'word', '最高级', 'block', '[]', ''),
  ('ad_banned', 'word', '最先进', 'block', '[]', ''),
  ('ad_banned', 'word', '第一', 'block', '[]', ''),
  ('ad_banned', 'word', '顶级', 'block', '[]', ''),
  ('ad_banned', 'word', '国家级', 'block', '[]', ''),
  ('ad_banned', 'word', '世界级', 'block', '[]', ''),
  ('ad_banned', 'word', '全网最', 'block', '[]', ''),
  ('ad_banned', 'word', '史上最', 'block', '[]', ''),
  ('ad_banned', 'word', '独一无二', 'block', '[]', ''),
  ('ad_banned', 'word', '绝无仅有', 'block', '[]', ''),
  ('ad_banned', 'word', '最权威', 'block', '[]', ''),
  ('ad_banned', 'word', '最专业', 'block', '[]', ''),
  ('ad_banned', 'word', '最靠谱', 'block', '[]', ''),
  ('ad_banned', 'word', '最全', 'block', '[]', ''),
  ('ad_banned', 'word', '最低价', 'block', '[]', ''),
  ('ad_banned', 'word', '首选', 'block', '[]', ''),
  ('ad_banned', 'word', '唯一', 'block', '[]', ''),
  ('ad_banned', 'word', '王牌', 'block', '[]', ''),
  ('ad_banned', 'word', '领导品牌', 'block', '[]', ''),
  ('ad_banned', 'word', '行业领先', 'block', '[]', ''),
  ('ad_banned', 'word', '遥遥领先', 'block', '[]', ''),
  ('ad_banned', 'word', 'NO.1', 'block', '[]', ''),
  ('ad_banned', 'word', '万能', 'block', '[]', ''),
  ('ad_banned', 'word', '零风险', 'block', '[]', ''),
  ('ad_banned', 'word', '无风险', 'block', '[]', ''),
  ('ad_banned', 'exception', '唯一的区别', 'block', '[]', ''),
  ('ad_banned', 'exception', '唯一要求', 'block', '[]', ''),
  ('ad_banned', 'exception', '唯一入口', 'block', '[]', ''),
  ('ad_banned', 'exception', '首选专业', 'block', '[]', ''),
  ('ad_banned', 'exception', '首选院校', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一年', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一学年', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一学期', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一轮', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一志愿', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一学历', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一作者', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一步', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一次', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一批', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一阶段', 'block', '[]', ''),
  ('ad_banned', 'exception', '第一选择', 'block', '[]', ''),
  ('admission_promise', 'word', '保证录取', 'block', '[]', ''),
  ('admission_promise', 'word', '确保录取', 'block', '[]', ''),
  ('admission_promise', 'word', '包录取', 'block', '[]', ''),
  ('admission_promise', 'word', '保录取', 'block', '[]', ''),
  ('admission_promise', 'word', '保录', 'block', '[]', ''),
  ('admission_promise', 'word', '包过', 'block', '[]', ''),
  ('admission_promise', 'word', '100%录取', 'block', '[]', ''),
  ('admission_promise', 'word', '百分百录取', 'block', '[]', ''),
  ('admission_promise', 'word', '百分之百录取', 'block', '[]', ''),
  ('admission_promise', 'word', '录取率100%', 'block', '[]', ''),
  ('admission_promise', 'word', '必录', 'block', '[]', ''),
  ('admission_promise', 'word', '必定录取', 'block', '[]', ''),
  ('admission_promise', 'word', '一定录取', 'block', '[]', ''),
  ('admission_promise', 'word', '稳拿offer', 'block', '[]', ''),
  ('admission_promise', 'word', '包offer', 'block', '[]', ''),
  ('admission_promise', 'word', '保offer', 'block', '[]', ''),
  ('admission_promise', 'word', '不录取全额退款', 'block', '[]', ''),
  ('admission_promise', 'word', '不录取退款', 'block', '[]', ''),
  ('admission_promise', 'word', '保底录取', 'block', '[]', ''),
  ('admission_promise', 'word', '保底名校', 'block', '[]', ''),
  ('admission_promise', 'word', '签约即录', 'block', '[]', ''),
  ('admission_promise', 'word', '稳录', 'block', '[]', ''),
  ('admission_promise', 'word', '稳进', 'block', '[]', ''),
  ('admission_promise', 'word', '稳上', 'block', '[]', ''),
  ('admission_promise', 'word', '包上岸', 'block', '[]', ''),
  ('admission_promise', 'word', '必上岸', 'block', '[]', ''),
  ('admission_promise', 'word', '保上岸', 'block', '[]', ''),
  ('admission_promise', 'word', '包名校', 'block', '[]', ''),
  ('admission_promise', 'word', '保名校', 'block', '[]', ''),
  ('admission_promise', 'word', '包拿offer', 'block', '[]', ''),
  ('admission_promise', 'word', '保拿offer', 'block', '[]', ''),
  ('admission_promise', 'word', 'offer保障', 'block', '[]', ''),
  ('admission_promise', 'word', '录取保障', 'block', '[]', ''),
  ('admission_promise', 'word', '无效退款', 'block', '[]', ''),
  ('admission_promise', 'word', '不过退款', 'block', '[]', ''),
  ('admission_promise', 'word', '未录取退费', 'block', '[]', ''),
  ('admission_promise', 'word', '拿不到offer退款', 'block', '[]', ''),
  ('admission_promise', 'word', '包签证', 'block', '[]', ''),
  ('admission_promise', 'word', '保签证', 'block', '[]', ''),
  ('admission_promise', 'word', '签证包过', 'block', '[]', ''),
  ('admission_promise', 'word', '包提分', 'block', '[]', ''),
  ('admission_promise', 'word', '保提分', 'block', '[]', ''),
  ('education_ad', 'word', '内部名额', 'block', '[]', ''),
  ('education_ad', 'word', '内部渠道', 'block', '[]', ''),
  ('education_ad', 'word', '内推名额', 'block', '[]', ''),
  ('education_ad', 'word', '直通名额', 'block', '[]', ''),
  ('education_ad', 'word', '特殊通道', 'block', '[]', ''),
  ('education_ad', 'word', '绿色通道', 'block', '[]', ''),
  ('education_ad', 'word', '招生官推荐', 'block', '[]', ''),
  ('education_ad', 'word', '招生官亲自', 'block', '[]', ''),
  ('education_ad', 'word', '招生办合作', 'block', '[]', ''),
  ('education_ad', 'word', '招生办内部', 'block', '[]', ''),
  ('education_ad', 'word', '学校官方合作', 'block', '[]', ''),
  ('education_ad', 'word', '官方授权', 'block', '[]', ''),
  ('education_ad', 'word', '官方指定', 'block', '[]', ''),
  ('education_ad', 'word', '唯一指定', 'block', '[]', ''),
  ('education_ad', 'word', '院校直录', 'block', '[]', ''),
  ('education_ad', 'word', '教授直推', 'block', '[]', ''),
  ('education_ad', 'word', '导师直推', 'block', '[]', ''),
  ('education_ad', 'word', '前招生官', 'block', '[]', ''),
  ('education_ad', 'word', '名校招生官', 'block', '[]', ''),
  ('education_ad', 'word', '出题人', 'block', '[]', ''),
  ('education_ad', 'word', '命题老师', 'block', '[]', ''),
  ('education_ad', 'word', '考官内部', 'block', '[]', ''),
  ('fact_claim', 'word', '录取率', 'cite', '[]', ''),
  ('fact_claim', 'word', '成功率', 'cite', '[]', ''),
  ('fact_claim', 'word', '通过率', 'cite', '[]', ''),
  ('fact_claim', 'word', '签约率', 'cite', '[]', ''),
  ('fact_claim', 'word', '满意度', 'cite', '[]', ''),
  ('fact_claim', 'word', '好评率', 'cite', '[]', ''),
  ('fact_claim', 'word', '服务过', 'cite', '[]', ''),
  ('fact_claim', 'word', '帮助过', 'cite', '[]', ''),
  ('fact_claim', 'word', '名额仅剩', 'cite', '[]', ''),
  ('fact_claim', 'word', '仅剩', 'cite', '[]', ''),
  ('fact_claim', 'word', '最后几个名额', 'cite', '[]', ''),
  ('fact_claim', 'word', '限时', 'cite', '[]', ''),
  ('diversion', 'word', '加微信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加我微信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加v', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加vx', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加wx', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加V信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加薇', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '威信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '薇信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', 'v信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', 'vx', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', 'wx', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '微信号', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '微信同号', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '企业微信', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '企微', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '公众号', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '服务号', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '扣扣', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', 'qq号', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '加q', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '手机号', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '电话联系', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '打电话', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '二维码', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '扫码', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '扫一扫', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '长按识别', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '链接在主页', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '主页有联系方式', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '看主页', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '戳主页', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '滴滴我', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', 'dd我', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '小程序', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '淘宝', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '闲鱼', 'block', '["xiaohongshu", "douyin", "channels", "zhihu"]', ''),
  ('diversion', 'word', '评论区扣1', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', '扣1', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', '扣“1”', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '点赞收藏', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '求点赞', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '求收藏', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '关注后私信', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '关注我领取', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '私信领取', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', '免费领取资料', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', '资料包', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', 'http://', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', 'https://', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', 'www.', 'block', '["douyin", "xiaohongshu"]', ''),
  ('diversion', 'word', '.com', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '.cn', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'exception', '资料包括', 'block', '["xiaohongshu"]', ''),
  ('diversion', 'word', '点赞关注', 'block', '["douyin"]', ''),
  ('diversion', 'word', '关注领取', 'block', '["douyin"]', ''),
  ('inducement', 'word', '转发朋友圈', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '分享到朋友圈领取', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '转发到群', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '分享到3个群', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '分享给好友领取', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '集赞', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '集齐', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '转发即可', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '转发抽奖', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '关注即送', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '关注领取', 'block', '["wechat_oa", "channels"]', ''),
  ('inducement', 'word', '不转不是', 'block', '["wechat_oa", "channels"]', ''),
  ('admission_promise', 'regex', '录取率\s*(达|高达|超过)?\s*\d{2,3}(\.\d+)?\s*%', 'block', '[]', '“录取率 XX%”'),
  ('admission_promise', 'regex', '\d{2,3}(\.\d+)?\s*%\s*(的)?\s*(录取|上岸|拿到?\s*offer)', 'block', '[]', '“98% 录取”“95% 拿 offer”'),
  ('admission_promise', 'regex', '(保|包|稳)\s*(拿|进|上|录)?\s*(G5|藤校|常春藤|名校|top\s*\d+|前\s*\d+\s*名?|[A-Za-z]{2,6})\s*(的)?\s*(录取|offer)', 'block', '[]', '“保 G5 录取”“稳进 top30 offer”（不命中“保研录取”“包括录取”）'),
  ('fact_claim', 'regex', '(累计|已|已经|成功)?\s*(帮助|服务|送出)\s*\d+\s*(余|多)?\s*(名|位|个)', 'cite', '[]', '“已帮助 3000 名学生”'),
  ('fact_claim', 'regex', '(仅剩|只剩|最后)\s*\d+\s*(个)?\s*名额', 'cite', '[]', '名额紧张话术'),
  ('privacy', 'regex', '(?<!\d)1[3-9]\d{9}(?!\d)', 'block', '[]', '手机号'),
  ('privacy', 'regex', '(?<![0-9Xx])\d{17}[0-9Xx](?![0-9Xx])', 'block', '[]', '身份证号'),
  ('privacy', 'regex', '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}', 'block', '[]', '邮箱（站点自身的联系邮箱在例外中配置）')
) as v(category, match_type, pattern, action, platforms, remark)
where not exists (select 1 from abr_compliance_rule r where r.category = v.category and r.match_type = v.match_type and lower(r.pattern) = lower(v.pattern));

-- ----------------------------
-- 4、菜单：获客运营 → 合规词表 + 按钮
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '合规词表', p.menu_id, 12, 'compliance', 'abroad/compliance/index', '', '', 1, 0, 'C', '0', '0', 'abroad:compliance:list', 'validCode', 'admin', now(), '合规词表菜单'
from (select menu_id from sys_menu where parent_id = 0 and path = 'growth') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:compliance:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select v.menu_name, c.menu_id, v.order_num, '', '', '', '', 1, 0, 'F', '0', '0', v.perms, '#', 'admin', now(), ''
from (values
  ('abroad:compliance:list', '规则查询', 1, 'abroad:compliance:query'),
  ('abroad:compliance:list', '规则新增', 2, 'abroad:compliance:add'),
  ('abroad:compliance:list', '规则修改', 3, 'abroad:compliance:edit'),
  ('abroad:compliance:list', '规则删除', 4, 'abroad:compliance:remove'),
  ('abroad:compliance:list', '试检查',   5, 'abroad:compliance:test'),
  ('abroad:compliance:list', '检查内容', 6, 'abroad:compliance:check')
) as v(parent_perms, menu_name, order_num, perms)
join sys_menu c on c.perms = v.parent_perms
where not exists (select 1 from sys_menu m where m.perms = v.perms);

-- ----------------------------
-- 5、触发规则：内容待过闸门 → 合规审查（content_writer 等生成内容后写 content_gate_requested 事件）
-- ----------------------------
insert into abr_growth_trigger (trigger_name, trigger_type, event_type, event_filter, cron_expression, playbook_id, agent_code, action, params,
                                delay_minutes, ttl_hours, max_attempts, status, create_by, create_time, remark)
select '内容待过闸门 → 合规审查', 'event', 'content_gate_requested', '{}'::jsonb, null, null, 'compliance_checker', 'check_content', '{}'::jsonb,
       0, 24, 3, '0', 'admin', now(), 'M6-03'
where not exists (select 1 from abr_growth_trigger t where t.agent_code = 'compliance_checker' and t.action = 'check_content');
