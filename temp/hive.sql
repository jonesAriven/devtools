-- 地市满意度及班组满意度环比分析查询（日累计版本）
-- 当前累计：当月1号到${taskid}的平均值
-- 上月累计：上月1号到${mtaskday}的平均值
-- 日期格式：20260401
-- 环比显示为百分比，保留1位小数
-- 所有满意度值先通过decimal(10,4)转换后再计算，空值统一处理为0
-- 优化：先按班组聚合，再关联地市表获取地市信息，最后按地市汇总拼接

select
 t1.city,  -- 地市
 '${taskid}' as statis_date,  -- 统计日期
 coalesce(t1.current_avg_score, 0) as current_date_satisfaction,  -- 当前累计满意度（当月1号至今）：按地市将班组平均值再求平均，保留2位小数
 coalesce(t2.last_month_avg_score, 0) as last_month_satisfaction,  -- 上月累计满意度（上月1号至同期）：按地市将班组平均值再求平均，保留2位小数
 case
 when coalesce(t2.last_month_avg_score, 0) = 0 then null  -- 上月满意度为0或空时环比为null
 when t1.current_avg_score is null then null  -- 当前满意度为空时环比为null
 else concat(round(((t1.current_avg_score / t2.last_month_avg_score) - 1) * 100, 1), '%')  -- 满意度环比 = (当前累计/上月累计 - 1) * 100%，保留1位小数
 end as satisfaction_ratio,  -- 满意度环比（百分比格式）
 coalesce(t3.current_concat_score, '无数据') as current_date_team_satisfaction,  -- 当前累计多班组满意度拼接：将班组满意度拼接，无数据时显示'无数据'
 coalesce(t4.last_month_concat_score, '无数据') as last_month_team_satisfaction,  -- 上月累计多班组满意度拼接：将班组满意度拼接，无数据时显示'无数据'
 coalesce(t5.channel_ratio_concat_str, '无数据') as team_satisfaction_ratio  -- 班组满意度环比拼接：每个班组的环比值拼接，无数据时显示'无数据'
from
(
 -- 子查询t1：计算当前累计各地市的平均满意度（先按班组聚合，再按地市求平均）
 select
 b.city,  -- 地市
 round(avg(a.team_avg_score), 2) as current_avg_score  -- 对班组平均值再求平均，保留2位小数
 from (
 -- 先按班组聚合，计算每个班组在当前累计期间的平均满意度
 select
 team_name,  -- 班组名称
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换，计算平均值后保留2位小数
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')  -- 当月1号，如：20260401
 and statis_date <= '${taskid}'  -- 到当前日期
 and final_satisfy_score is not null  -- 过滤空值
 and final_satisfy_score != ''  -- 过滤空字符串
 group by team_name  -- 按班组分组
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name and b.statis_date = '${taskid}'  -- 关联班组归属表获取地市
 group by b.city  -- 按地市分组
) t1
left join
(
 -- 子查询t2：计算上月累计各地市的平均满意度（先按班组聚合，再按地市求平均）
 select
 b.city,  -- 地市
 round(avg(a.team_avg_score), 2) as last_month_avg_score  -- 对班组平均值再求平均，保留2位小数
 from (
 -- 先按班组聚合，计算每个班组在上月累计期间的平均满意度
 select
 team_name,  -- 班组名称
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换，计算平均值后保留2位小数
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')  -- 上月1号，如：20260301
 and statis_date <= '${mtaskday}'  -- 到上月同期日期
 and final_satisfy_score is not null  -- 过滤空值
 and final_satisfy_score != ''  -- 过滤空字符串
 group by team_name  -- 按班组分组
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name and b.statis_date = '${taskid}'  -- 关联班组归属表获取地市
 group by b.city  -- 按地市分组
) t2 on t1.city = t2.city  -- 按地市关联
left join
(
 -- 子查询t3：拼接当前累计各地市下多班组的满意度
 select
 b.city,  -- 地市
 concat_ws('/', collect_list(concat(a.team_name, '的满意度为', a.team_avg_score))) as current_concat_score
 -- 使用'/'作为分隔符拼接多个班组，格式：班组a的满意度为****/班组b的满意度为****
 from (
 -- 先按班组聚合，计算每个班组在当前累计期间的平均满意度
 select
 team_name,  -- 班组名称
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换，计算平均值后保留2位小数
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')  -- 当月1号
 and statis_date <= '${taskid}'  -- 到当前日期
 and final_satisfy_score is not null  -- 过滤空值
 and final_satisfy_score != ''  -- 过滤空字符串
 group by team_name  -- 按班组分组
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name and b.statis_date = '${taskid}'  -- 关联班组归属表获取地市
 group by b.city  -- 按地市分组
) t3 on t1.city = t3.city  -- 按地市关联
left join
(
 -- 子查询t4：拼接上月累计各地市下多班组的满意度
 select
 b.city,  -- 地市
 concat_ws('/', collect_list(concat(a.team_name, '的满意度为', a.team_avg_score))) as last_month_concat_score
 -- 使用'/'作为分隔符拼接多个班组，格式：班组a的满意度为****/班组b的满意度为****
 from (
 -- 先按班组聚合，计算每个班组在上月累计期间的平均满意度
 select
 team_name,  -- 班组名称
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换，计算平均值后保留2位小数
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')  -- 上月1号
 and statis_date <= '${mtaskday}'  -- 到上月同期日期
 and final_satisfy_score is not null  -- 过滤空值
 and final_satisfy_score != ''  -- 过滤空字符串
 group by team_name  -- 按班组分组
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name and b.statis_date = '${taskid}'  -- 关联班组归属表获取地市
 group by b.city  -- 按地市分组
) t4 on t1.city = t4.city  -- 按地市关联
left join
(
 -- 子查询t5：计算每个班组的环比并拼接
 select
 city,  -- 地市
 concat_ws('/', collect_list(concat(team_name, '的环比为', team_ratio_str))) as channel_ratio_concat_str
 -- 使用'/'作为分隔符拼接多个班组的环比，格式：班组a的环比为**%/班组b的环比为**%
 from (
 -- 关联当前累计和上月累计的班组数据，计算每个班组的环比
 select
 coalesce(c1.city, c2.city) as city,  -- 取非空的地市名
 coalesce(c1.team_name, c2.team_name) as team_name,  -- 取非空的班组名
 case
 when c2.team_avg_score is null or c2.team_avg_score = 0 then 'NULL'  -- 上月累计数据为0或空时环比为NULL
 when c1.team_avg_score is null then 'NULL'  -- 当前累计数据为空时环比为NULL
 else concat(round(((c1.team_avg_score / c2.team_avg_score) - 1) * 100, 1), '%')  -- 班组环比 = (当前累计/上月累计 - 1) * 100%，保留1位小数
 end as team_ratio_str  -- 班组环比字符串
 from (
 -- 当前累计班组数据（带地市）
 select
 b.city,  -- 地市
 a.team_name,  -- 班组名称
 a.team_avg_score  -- 当前累计平均满意度
 from (
 select
 team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')  -- 当月1号
 and statis_date <= '${taskid}'  -- 到当前日期
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 group by team_name
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name and b.statis_date = '${taskid}'
 ) c1
 full outer join (  -- 全外连接，确保即使某班组只有当前或只有上月数据也能保留
 -- 上月累计班组数据（带地市）
 select
 b.city,  -- 地市
 a.team_name,  -- 班组名称
 a.team_avg_score  -- 上月累计平均满意度
 from (
 select
 team_name,
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as team_avg_score
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')  -- 上月1号
 and statis_date <= '${mtaskday}'  -- 到上月同期日期
 and final_satisfy_score is not null
 and final_satisfy_score != ''
 group by team_name
 ) a
 inner join csap270.TB_IN_BELONG_TEAM b on a.team_name = b.team_name and b.statis_date = '${taskid}'
 ) c2 on c1.city = c2.city and c1.team_name = c2.team_name  -- 按地市和班组关联
 ) d
 group by city  -- 按地市分组，收集每个地市下所有班组的环比信息
) t5 on t1.city = t5.city  -- 按地市关联