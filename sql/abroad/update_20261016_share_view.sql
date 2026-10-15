-- ----------------------------
-- M4-13 分享：家长只读视图（plan_view）与选校单分享卡（plan_card）
-- 分享可撤销：撤销后链接失效，再次分享时换新的分享码；记录查看次数与最近查看时间（家长查看事件，供 M4-14 转化时刻）
-- ----------------------------
alter table abr_share add column if not exists revoked        boolean   default false;
alter table abr_share add column if not exists view_count     integer   default 0;
alter table abr_share add column if not exists last_view_time timestamp default null;

comment on column abr_share.kind           is '分享对象类型（report_poster 定位海报 / plan_view 家长只读视图 / plan_card 选校单分享卡）';
comment on column abr_share.ref_id         is '分享对象ID（定位海报为测评ID；选校单为访客ID）';
comment on column abr_share.revoked        is '是否已撤销（撤销后链接失效，再次分享换新分享码）';
comment on column abr_share.view_count     is '查看次数（家长只读视图被打开的次数）';
comment on column abr_share.last_view_time is '最近查看时间';
