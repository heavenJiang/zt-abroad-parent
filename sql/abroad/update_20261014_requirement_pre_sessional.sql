-- ----------------------------------------------------------------------------
-- 申请要求加“是否接受语言班”（课前语言 pre-sessional），供选校引擎硬门槛判断“可读语言班”
-- 0 不接受 / 1 接受 / 2 未知（未知时按国家规则 country_rules.pre_sessional 判断，并标“待核实”）
-- 可重复执行（add column if not exists / where not exists）
-- ----------------------------------------------------------------------------

alter table abr_requirement add column if not exists accept_pre_sessional char(1) default '2';
comment on column abr_requirement.accept_pre_sessional is '是否接受语言班（课前语言，字典 abr_pre_sessional：0不接受 1接受 2未知）';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '语言班', 'abr_pre_sessional', '0', 'admin', now(), '项目是否接受语言班（课前语言）'
where not exists (select 1 from sys_dict_type t where t.dict_type = 'abr_pre_sessional');

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time)
select v.dict_sort, v.dict_label, v.dict_value, 'abr_pre_sessional', v.list_class, 'N', '0', 'admin', now()
from (values
  (1, '不接受', '0', 'danger'),
  (2, '接受',   '1', 'success'),
  (3, '未知',   '2', 'info')
) as v(dict_sort, dict_label, dict_value, list_class)
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_pre_sessional' and d.dict_value = v.dict_value);
