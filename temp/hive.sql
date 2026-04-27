-- 地市满意度及班组满意度环比分析查询（日累计版本）
-- 当前累计：当月1号到${taskid}的平均值
-- 上月累计：上月1号到${mtaskday}的平均值
-- 日期格式：20260401
-- 环比显示为百分比，保留1位小数
-- 所有满意度值先通过decimal(10,4)转换后再计算，空值统一处理为0
select
 t1.city,  -- 地市
 '${taskid}' as statis_date,  -- 统计日期
 coalesce(t1.current_avg_score, 0) as current_date_satisfaction,  -- 当前累计满意度（当月1号至今）：按地市将final_satisfy_score求平均值，空值处理为0，保留2位小数
 coalesce(t2.last_month_avg_score, 0) as last_month_satisfaction,  -- 上月累计满意度（上月1号至同期）：按地市将final_satisfy_score求平均值，空值处理为0，保留2位小数
 case
 when coalesce(t2.last_month_avg_score, 0) = 0 then null  -- 上月满意度为0或空时环比为null
 when t1.current_avg_score is null then null  -- 当前满意度为空时环比为null
 else concat(round(((t1.current_avg_score / t2.last_month_avg_score) - 1) * 100, 1), '%')  -- 满意度环比 = (当前累计/上月累计 - 1) * 100%，保留1位小数
 end as satisfaction_ratio,  -- 满意度环比（百分比格式）
 coalesce(t3.current_concat_score, '无数据') as current_date_team_satisfaction,  -- 当前累计多班组满意度拼接：将当月1号至今各地市多班组的final_satisfy_score直接拼接，无数据时显示'无数据'
 coalesce(t4.last_month_concat_score, '无数据') as last_month_team_satisfaction,  -- 上月累计多班组满意度拼接：将上月1号至同期各地市多班组的final_satisfy_score直接拼接，无数据时显示'无数据'
 coalesce(t5.channel_ratio_concat_str, '无数据') as team_satisfaction_ratio  -- 班组满意度环比拼接：每个班组的环比值拼接，无数据时显示'无数据'
from
(
 -- 子查询t1：计算当前累计（当月1号到${taskid}）各地市的平均满意度
 select
 city,  -- 地市
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as current_avg_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换，计算平均值后保留2位小数
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')  -- 当月1号，如：20260401
 and statis_date <= '${taskid}'  -- 到当前日期
 group by city  -- 按地市分组
) t1
left join
(
 -- 子查询t2：计算上月累计（上月1号到${mtaskday}）各地市的平均满意度
 select
 city,  -- 地市
 round(avg(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4))), 2) as last_month_avg_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换，计算平均值后保留2位小数
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')  -- 上月1号，如：20260301
 and statis_date <= '${mtaskday}'  -- 到上月同期日期
 group by city  -- 按地市分组
) t2 on t1.city = t2.city  -- 按地市关联
left join
(
 -- 子查询t3：拼接当前累计（当月1号到${taskid}）各地市下多班组的满意度
 select
 city,  -- 地市
 concat_ws('/',   -- 使用'/'作为分隔符拼接多个班组
 collect_list(  -- 收集每个班组的满意度字符串
 concat(channel_name, '的满意度为',   -- 班组名称
 round(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4)), 2)  -- 满意度值，空值转为0，保留2位小数
 )
 )
 ) as current_concat_score  -- 当前累计多班组满意度拼接结果
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')  -- 当月1号
 and statis_date <= '${taskid}'  -- 到当前日期
 and channel_name is not null  -- 过滤班组名为空的数据
 group by city  -- 按地市分组
) t3 on t1.city = t3.city  -- 按地市关联
left join
(
 -- 子查询t4：拼接上月累计（上月1号到${mtaskday}）各地市下多班组的满意度
 select
 city,  -- 地市
 concat_ws('/',   -- 使用'/'作为分隔符拼接多个班组
 collect_list(  -- 收集每个班组的满意度字符串
 concat(channel_name, '的满意度为',   -- 班组名称
 round(cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4)), 2)  -- 满意度值，空值转为0，保留2位小数
 )
 )
 ) as last_month_concat_score  -- 上月累计多班组满意度拼接结果
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')  -- 上月1号
 and statis_date <= '${mtaskday}'  -- 到上月同期日期
 and channel_name is not null  -- 过滤班组名为空的数据
 group by city  -- 按地市分组
) t4 on t1.city = t4.city  -- 按地市关联
left join
(
 -- 子查询t5：计算每个班组的环比并拼接（累计版本）
 select
 city,  -- 地市
 concat_ws('/',   -- 使用'/'作为分隔符拼接多个班组
 collect_list(  -- 收集每个班组的环比字符串
 concat(channel_name, '的环比为',   -- 班组名称
 case
 when last_month_score is null or last_month_score = 0 then 'NULL'  -- 上月累计数据为0或空时环比为NULL
 when current_score is null then 'NULL'  -- 当前累计数据为空时环比为NULL
 else concat(round(((current_score / last_month_score) - 1) * 100, 1), '%')  -- 班组环比 = (当前累计/上月累计 - 1) * 100%，保留1位小数
 end
 )
 )
 ) as channel_ratio_concat_str  -- 班组满意度环比拼接结果
 from
 (
 -- 内层子查询：关联当前累计和上月累计的班组数据
 select
 coalesce(a.city, b.city) as city,  -- 取非空的地市名
 coalesce(a.channel_name, b.channel_name) as channel_name,  -- 取非空的班组名
 a.current_score,  -- 当前累计的满意度分数
 b.last_month_score  -- 上月累计的满意度分数
 from
 (
 -- 当前累计班组数据（当月1号到${taskid}）
 select
 city,  -- 地市
 channel_name,  -- 班组
 cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4)) as current_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${taskid}', 1, 6), '01')  -- 当月1号
 and statis_date <= '${taskid}'  -- 到当前日期
 and channel_name is not null  -- 过滤班组名为空的数据
 ) a
 full outer join   -- 全外连接，确保即使某班组只有当前或只有上月数据也能保留
 (
 -- 上月累计班组数据（上月1号到${mtaskday}）
 select
 city,  -- 地市
 channel_name,  -- 班组
 cast(coalesce(nullif(final_satisfy_score, ''), '0') as decimal(10,4)) as last_month_score
 -- 将final_satisfy_score转为decimal(10,4)，空值和空字符串转为'0'后转换
 from temp_tb_dw_mk_local_operation_270_day_03_${taskid}
 where statis_date >= concat(substr('${mtaskday}', 1, 6), '01')  -- 上月1号
 and statis_date <= '${mtaskday}'  -- 到上月同期日期
 and channel_name is not null  -- 过滤班组名为空的数据
 ) b
 on a.city = b.city and a.channel_name = b.channel_name  -- 按地市和班组关联
 ) c
 group by city  -- 按地市分组，收集每个地市下所有班组的环比信息
) t5 on t1.city = t5.city  -- 按地市关联