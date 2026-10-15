-- ----------------------------
-- M4-08 分享记录：每个访客的每份报告（及后续选校单等）一个稳定的分享码，二维码链接带 ch=share&ct=分享码，
-- 新访客经二维码进入时渠道记为 share、内容编号记为分享码，可追到分享人（不在图中暴露任何个人信息）。
-- ----------------------------
create table if not exists abr_share (
  share_id       bigserial      not null,
  share_code     varchar(16)    not null,
  kind           varchar(32)    not null,
  visitor_id     bigint         not null,
  ref_id         bigint         not null,
  options        jsonb          default '{}'::jsonb,
  render_count   integer        default 0,
  create_time    timestamp      default now(),
  update_time    timestamp      default now(),
  primary key (share_id)
);
create unique index if not exists uk_abr_share_code on abr_share (share_code);
create unique index if not exists uk_abr_share_ref on abr_share (kind, visitor_id, ref_id);

comment on table  abr_share              is '分享记录（定位海报等；每个访客的每个分享对象一条，分享码稳定）';
comment on column abr_share.share_id     is '分享ID';
comment on column abr_share.share_code   is '分享码（二维码链接中的 ct，随机 10 位）';
comment on column abr_share.kind         is '分享对象类型（report_poster 定位海报）';
comment on column abr_share.visitor_id   is '分享人访客ID';
comment on column abr_share.ref_id       is '分享对象ID（定位海报为测评ID）';
comment on column abr_share.options      is '最近一次选择的显示项（showSchool 本科学校、showGpa 均分；默认都不显示）';
comment on column abr_share.render_count is '生成图片次数';
comment on column abr_share.create_time  is '创建时间';
comment on column abr_share.update_time  is '最近生成时间';
