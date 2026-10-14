-- ----------------------------------------------------------------------------
-- 学位 / 学段字典加入“高中”（美高），覆盖全学段：高中、本科、硕士、博士，并按学段顺序排序
-- 可重复执行（where not exists / 只在值不同时更新）
-- ----------------------------------------------------------------------------

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select 1, '高中', 'secondary', 'abr_degree', 'success', 'N', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_degree' and d.dict_value = 'secondary');

update sys_dict_data d
   set dict_sort = v.dict_sort, update_by = 'admin', update_time = now()
  from (values ('secondary', 1), ('bachelor', 2), ('master', 3), ('phd', 4)) as v(dict_value, dict_sort)
 where d.dict_type = 'abr_degree'
   and d.dict_value = v.dict_value
   and d.dict_sort <> v.dict_sort;

update sys_dict_type
   set dict_name = '学位 / 学段', remark = '项目学位 / 申请学段（高中、本科、硕士、博士）', update_by = 'admin', update_time = now()
 where dict_type = 'abr_degree'
   and remark is distinct from '项目学位 / 申请学段（高中、本科、硕士、博士）';
