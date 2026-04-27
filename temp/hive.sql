insert overwrite table temp_tb_dw_mk_local_operation_270_dtal_satisfy_03_${taskid}
select
 city.city,  -- 地市
 '${taskid}' as statis_date,  -- 统计日期
 cast(coalesce(t1.current_avg_score, 0) as string) as current_date_satisfaction,  -- 当前累计满意度
 cast(coalesce(t2.last_month_avg_score, 0) as string) as last_month_satisfaction,  -- 上月累计满意度
 case
 when coalesce(t2.last_month_avg_score, 0) = 0 then null
 when t1.current_avg_score is null then null
 else concat(round(((t1.current_avg_score / t2.last_month_avg_score) - 1) * 100, 1), '%')
 end as satisfaction_ratio,
 case
 when coalesce(t2.last_month_avg_score, 0) = 0 then null
 when t1.current_avg_score is null then null
 else round(((t1.current_avg_score / t2.last_month_avg_score) - 1), 4)
 end as satisfaction_ratio_value,
 coalesce(t3.current_concat_score, '无数据') as current_date_team_satisfaction,
 coalesce(t4.last_month_concat_score, '无数据') as last_month_team_satisfaction,
 coalesce(t5.channel_ratio_concat_str, '无数据') as team_satisfaction_ratio
from
(
 -- 主表：获取所有地市
 select distinct city
 from csap270.TB_IN_BELONG_TEAM
) city
left join
(
 -- 子查询t1：计算当前累计各地市的平均满意度
 select
 b.city,
 round(avg(a.team_avg_score), 2) as current_avg_score
 from (
 select
 trim(channel_name) as team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')
 and statis_date <= '${taskid}'
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 and channel_name is not null
 and trim(channel_name) != ''
 group by trim(channel_name)
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name
 group by b.city
) t1 on city.city = t1.city
left join
(
 -- 子查询t2：计算上月累计各地市的平均满意度
 select
 b.city,
 round(avg(a.team_avg_score), 2) as last_month_avg_score
 from (
 select
 trim(channel_name) as team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')
 and statis_date <= '${mtaskday}'
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 and channel_name is not null
 and trim(channel_name) != ''
 group by trim(channel_name)
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name
 group by b.city
) t2 on city.city = t2.city
left join
(
 -- 子查询t3：拼接当前累计各地市下多班组的满意度
 select
 b.city,
 concat_ws('/', collect_list(concat(a.team_name, '的满意度为', a.team_avg_score))) as current_concat_score
 from (
 select
 trim(channel_name) as team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')
 and statis_date <= '${taskid}'
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 and channel_name is not null
 and trim(channel_name) != ''
 group by trim(channel_name)
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name
 group by b.city
) t3 on city.city = t3.city
left join
(
 -- 子查询t4：拼接上月累计各地市下多班组的满意度
 select
 b.city,
 concat_ws('/', collect_list(concat(a.team_name, '的满意度为', a.team_avg_score))) as last_month_concat_score
 from (
 select
 trim(channel_name) as team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')
 and statis_date <= '${mtaskday}'
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 and channel_name is not null
 and trim(channel_name) != ''
 group by trim(channel_name)
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name
 group by b.city
) t4 on city.city = t4.city
left join
(
 -- 子查询t5：计算每个班组的环比并拼接
 select
 city,
 concat_ws('/', collect_list(concat(team_name, '的环比为', team_ratio_str))) as channel_ratio_concat_str
 from (
 select
 coalesce(c1.city, c2.city) as city,
 coalesce(c1.team_name, c2.team_name) as team_name,
 case
 when c2.team_avg_score is null or c2.team_avg_score = 0 then null
 when c1.team_avg_score is null then null
 else concat(round(((c1.team_avg_score / c2.team_avg_score) - 1) * 100, 1), '%')
 end as team_ratio_str
 from (
 select
 b.city,
 a.team_name,
 a.team_avg_score
 from (
 select
 trim(channel_name) as team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')
 and statis_date <= '${taskid}'
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 and channel_name is not null
 and trim(channel_name) != ''
 group by trim(channel_name)
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name
 ) c1
 full outer join (
 select
 b.city,
 a.team_name,
 a.team_avg_score
 from (
 select
 trim(channel_name) as team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')
 and statis_date <= '${mtaskday}'
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 and channel_name is not null
 and trim(channel_name) != ''
 group by trim(channel_name)
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name
 ) c2 on c1.city = c2.city and c1.team_name = c2.team_name
 ) d
 group by city
) t5 on city.city = t5.city