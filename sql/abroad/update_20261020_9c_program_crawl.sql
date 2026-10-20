-- ----------------------------------------------------------------------------
-- M2-08 项目自动采集（爬虫，替代人工录入）
--
-- - abr_crawl_site：每所学校一个采集站点（官网、robots 声明的站点地图、抓取间隔、最近一次发现的结果）。
-- - abr_crawl_page：待抓取 / 已抓取的页面（项目页候选或课程目录页），记录状态、存档、抽出的字段与建立的项目。
-- - 定时任务：abrCrawlTask.discover() 每天 02:30 为到期的站点从站点地图与课程目录发现项目页；
--   abrCrawlTask.fetch() 每 2 分钟按站点的抓取间隔低频抓取到期页面：robots 允许才抓，原文存档（M2-09），
--   规则抽取（+ 真实大模型时 catalog_extractor）后经校验器核对引句，自动建立项目与申请要求（来源方式“自动采集”）。
-- - 均分、语言、截止日都有可靠来源的项目才上架（状态正常），缺的先停用待补；员工改过的字段不被采集覆盖。
-- 依赖：update_20261020_9a_source_archive.sql
-- 可重复执行
-- ----------------------------------------------------------------------------

create table if not exists abr_crawl_site (
  site_id              bigserial      not null,
  school_id            bigint         not null,
  base_url             varchar(500)   not null,
  status               char(1)        default '0',
  sitemaps             jsonb          not null default '[]'::jsonb,
  crawl_delay          integer        default 0,
  last_discover_time   timestamp      default null,
  last_discover_result varchar(500)   default '',
  create_time          timestamp      default now(),
  update_time          timestamp      default null,
  remark               varchar(500)   default null,
  primary key (site_id)
);
create unique index if not exists uk_abr_crawl_site_school on abr_crawl_site (school_id);

comment on table  abr_crawl_site                      is '项目采集站点（每所学校一个）';
comment on column abr_crawl_site.site_id              is '站点ID';
comment on column abr_crawl_site.school_id            is '学校';
comment on column abr_crawl_site.base_url             is '官网';
comment on column abr_crawl_site.status               is '状态（0 采集 1 暂停）';
comment on column abr_crawl_site.sitemaps             is 'robots.txt 声明的站点地图';
comment on column abr_crawl_site.crawl_delay          is 'robots.txt 要求的抓取间隔（秒，0 为未声明）';
comment on column abr_crawl_site.last_discover_time   is '最近一次发现时间';
comment on column abr_crawl_site.last_discover_result is '最近一次发现的结果';
comment on column abr_crawl_site.create_time          is '创建时间';
comment on column abr_crawl_site.update_time          is '更新时间';
comment on column abr_crawl_site.remark               is '备注';

create table if not exists abr_crawl_page (
  page_id          bigserial      not null,
  site_id          bigint         not null,
  school_id        bigint         not null,
  url              varchar(1000)  not null,
  kind             varchar(16)    not null default 'program',
  depth            integer        default 0,
  found_from       varchar(16)    default 'sitemap',
  link_text        varchar(200)   default '',
  status           varchar(16)    not null default 'new',
  title            varchar(300)   default '',
  snapshot_id      bigint         default null,
  content_hash     varchar(64)    default '',
  program_id       bigint         default null,
  requirement_id   bigint         default null,
  fields_found     integer        default 0,
  result           jsonb          default null,
  fail_count       integer        default 0,
  reason           varchar(300)   default '',
  fetched_time     timestamp      default null,
  next_fetch_time  timestamp      default now(),
  create_time      timestamp      default now(),
  update_time      timestamp      default null,
  primary key (page_id)
);
create unique index if not exists uk_abr_crawl_page_url on abr_crawl_page (url);
create index if not exists idx_abr_crawl_page_due on abr_crawl_page (status, next_fetch_time);
create index if not exists idx_abr_crawl_page_school on abr_crawl_page (school_id, status);

comment on table  abr_crawl_page                 is '项目采集页面';
comment on column abr_crawl_page.page_id         is '页面ID';
comment on column abr_crawl_page.site_id         is '站点';
comment on column abr_crawl_page.school_id       is '学校';
comment on column abr_crawl_page.url             is '网址';
comment on column abr_crawl_page.kind            is '类型（program 项目页候选 / directory 课程目录页）';
comment on column abr_crawl_page.depth           is '从目录页向下的层数';
comment on column abr_crawl_page.found_from      is '发现途径（sitemap 站点地图 / link 页面链接 / home 官网首页）';
comment on column abr_crawl_page.link_text       is '链接文字';
comment on column abr_crawl_page.status          is '状态（字典 abr_crawl_status：new 待抓取 / done 已采集 / listed 目录已展开 / not_program 不是项目页 / blocked robots 不允许 / failed 失败）';
comment on column abr_crawl_page.title           is '页面标题';
comment on column abr_crawl_page.snapshot_id     is '最近一次存档';
comment on column abr_crawl_page.content_hash    is '最近一次内容哈希（没变化不重新抽取）';
comment on column abr_crawl_page.program_id      is '建立 / 更新的项目';
comment on column abr_crawl_page.requirement_id  is '建立 / 更新的申请要求';
comment on column abr_crawl_page.fields_found    is '通过校验的字段数';
comment on column abr_crawl_page.result          is '逐字段结果（字段、取值、引句、状态、原因）';
comment on column abr_crawl_page.fail_count      is '连续失败次数';
comment on column abr_crawl_page.reason          is '原因（不是项目页、robots、失败原因、未上架原因等）';
comment on column abr_crawl_page.fetched_time    is '最近一次抓取时间';
comment on column abr_crawl_page.next_fetch_time is '下次抓取时间';
comment on column abr_crawl_page.create_time     is '发现时间';
comment on column abr_crawl_page.update_time     is '更新时间';

-- ----------------------------
-- 字典
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '采集页面状态', 'abr_crawl_status', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_type where dict_type = 'abr_crawl_status');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_crawl_status', v.list_class, 'N', '0', 'admin', now(), ''
from (values
  (1, '待抓取',         'new',         'info'),
  (2, '已采集',         'done',        'success'),
  (3, '目录已展开',     'listed',      'primary'),
  (4, '不是项目页',     'not_program', 'info'),
  (5, 'robots 不允许',  'blocked',     'warning'),
  (6, '失败',           'failed',      'danger')
) as v(dict_sort, dict_label, dict_value, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_crawl_status' and d.dict_value = v.dict_value);

-- ----------------------------
-- 菜单：院校库 → 项目采集
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '项目采集', p.menu_id, 4, 'crawl', 'abroad/crawl/index', '', 'Crawl', 1, 0, 'C', '0', '0', 'abroad:crawl:list', 'guide', 'admin', now(), '项目自动采集：站点、页面、抽取结果'
from (select menu_id from sys_menu where parent_id = 0 and path = 'catalog') p
where not exists (select 1 from sys_menu m where m.perms = 'abroad:crawl:list');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '立即采集', c.menu_id, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'abroad:crawl:run', '#', 'admin', now(), '发现、抓取、重抓、暂停站点'
from sys_menu c
where c.perms = 'abroad:crawl:list' and not exists (select 1 from sys_menu m where m.perms = 'abroad:crawl:run');

insert into sys_role_menu (role_id, menu_id)
select r.role_id, m.menu_id
from sys_role r join sys_menu m on m.perms like 'abroad:crawl:%'
where r.role_key in ('abr_owner', 'abr_editor') and r.del_flag = '0'
on conflict do nothing;

-- ----------------------------
-- 定时任务
-- ----------------------------
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select v.name, 'DEFAULT', v.target, v.cron, '3', '1', '0', 'admin', now(), v.remark
from (values
  ('项目采集：发现项目页', 'abrCrawlTask.discover()', '0 30 2 * * ?', 'M2-08：为到期的站点从站点地图与课程目录发现项目页'),
  ('项目采集：抓取与抽取', 'abrCrawlTask.fetch()',    '0 0/2 * * * ?', 'M2-08：按站点抓取间隔低频抓取到期页面，存档、抽取、自动建立项目与申请要求')
) as v(name, target, cron, remark)
where not exists (select 1 from sys_job j where j.invoke_target = v.target);
