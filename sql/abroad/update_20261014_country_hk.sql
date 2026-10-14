-- ----------------------------------------------------------------------------
-- 留学国家 / 地区字典加入中国香港，并按主做方向调整排序：
--   美国、英国、澳大利亚、新加坡、中国香港、日本（主做）→ 韩国、德国（保留）
-- 可重复执行（where not exists / 只在值不同时更新）
-- ----------------------------------------------------------------------------

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, list_class, is_default, status, create_by, create_time, remark)
select 5, '中国香港', 'HK', 'abr_country', 'default', 'N', '0', 'admin', now(), ''
where not exists (select 1 from sys_dict_data d where d.dict_type = 'abr_country' and d.dict_value = 'HK');

update sys_dict_data d
   set dict_sort = v.dict_sort, update_by = 'admin', update_time = now()
  from (values ('US', 1), ('UK', 2), ('AU', 3), ('SG', 4), ('HK', 5), ('JP', 6), ('KR', 7), ('DE', 8)) as v(dict_value, dict_sort)
 where d.dict_type = 'abr_country'
   and d.dict_value = v.dict_value
   and d.dict_sort <> v.dict_sort;

update sys_dict_type
   set remark = '院校所在国家 / 地区（主做：美、英、澳、新、港、日）', update_by = 'admin', update_time = now()
 where dict_type = 'abr_country'
   and remark is distinct from '院校所在国家 / 地区（主做：美、英、澳、新、港、日）';
