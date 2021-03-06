package cn.uncode.schedule;

import cn.uncode.schedule.core.ScheduledMethodRunnable;
import cn.uncode.schedule.core.TaskDefine;
import cn.uncode.schedule.util.ScheduleUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态任务管理
 *
 * @author liuht
 */
public class DynamicTaskManager {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(DynamicTaskManager.class);
    /**
     * 缓存 scheduleKey 与 ScheduledFuture,删除任务时可以优雅的关闭任务
     */
    private static final Map<String, ScheduledFuture<?>> SCHEDULE_FUTURES = new ConcurrentHashMap<String, ScheduledFuture<?>>();
    /**
     * 缓存 scheduleKey 与 TaskDefine任务具体信息
     */
    private static final Map<String, TaskDefine> TASKS = new ConcurrentHashMap<String, TaskDefine>();

    /**
     * 启动定时任务
     *
     * @param taskDefine  定时任务
     * @param currentTime 时间
     */
    public static void scheduleTask(TaskDefine taskDefine, Date currentTime) {
        LOGGER.info("开始启动定时任务: "+ taskDefine.stringKey());
        boolean newTask = true;
        if (SCHEDULE_FUTURES.containsKey(taskDefine.stringKey())) {
            if (taskDefine.equals(TASKS.get(taskDefine.stringKey()))) {
                LOGGER.info("定时任务已经存在: "+ taskDefine.stringKey() + ", 不需要重新构建");
                newTask = false;
            }
        }
        if (newTask) {
            TASKS.put(taskDefine.stringKey(), taskDefine);
            scheduleTask(taskDefine.getTargetBean(), taskDefine.getTargetMethod(),
                    taskDefine.getCronExpression(), taskDefine.getStartTime(), taskDefine.getPeriod(),
                    taskDefine.getParams(), taskDefine.getExtKeySuffix(), false);
            LOGGER.info("成功添加动态任务: "+ taskDefine.stringKey());
        }
    }

    /**
     * 清理本地任务
     *
     * @param existsTaskName 与本地缓存的任务列表 两者进行比对
     *                       如果远程没有了,本地还有,就要清除本地数据
     *                       并且停止本地任务cancel(true)
     */
    public static void clearLocalTask(List<String> existsTaskName) {
        for (String name : SCHEDULE_FUTURES.keySet()) {
            if (!existsTaskName.contains(name)) {
                SCHEDULE_FUTURES.get(name).cancel(true);
                SCHEDULE_FUTURES.remove(name);
                TASKS.remove(name);
                LOGGER.info("清理定时任务: "+ name);
            }
        }
    }

    /**
     * 启动动态定时任务
     * 支持：
     * 1 cron时间表达式，立即执行
     * 2 startTime + period,指定时间，定时进行
     * 3 period，定时进行，立即开始
     * 4 startTime，指定时间执行
     *
     * @param targetBean     目标bean名称
     * @param targetMethod   方法
     * @param cronExpression cron表达式
     * @param startTime      指定执行时间
     * @param period         定时进行，立即开始
     * @param params         给方法传递的参数
     * @param extKeySuffix   任务后缀名
     * @param onlyOne        备用字段
     */
    public static void scheduleTask(String targetBean, String targetMethod, String cronExpression, Date startTime, long period, String params, String extKeySuffix, boolean onlyOne) {
        String scheduleKey = ScheduleUtil.buildScheduleKey(targetBean, targetMethod, extKeySuffix);
        try {
            if (!SCHEDULE_FUTURES.containsKey(scheduleKey)) {
                ScheduledFuture<?> scheduledFuture = null;
                ScheduledMethodRunnable scheduledMethodRunnable = buildScheduledRunnable(targetBean, targetMethod, params, extKeySuffix, onlyOne);
                if (scheduledMethodRunnable != null) {
                    if (StringUtils.isNotEmpty(cronExpression)) {
                        Trigger trigger = new CronTrigger(cronExpression);
                        scheduledFuture = ConsoleManager.getSchedulerTaskManager().schedule(scheduledMethodRunnable, trigger);
                    } else if (startTime != null) {
                        if (period > 0) {
                            scheduledFuture = ConsoleManager.getSchedulerTaskManager().scheduleAtFixedRate(scheduledMethodRunnable, startTime, period);
                        } else {
                            scheduledFuture = ConsoleManager.getSchedulerTaskManager().schedule(scheduledMethodRunnable, startTime);
                        }
                    } else if (period > 0) {
                        scheduledFuture = ConsoleManager.getSchedulerTaskManager().scheduleAtFixedRate(scheduledMethodRunnable, period);
                    }
                    if (null != scheduledFuture) {
                        SCHEDULE_FUTURES.put(scheduleKey, scheduledFuture);
                        LOGGER.debug("Building new schedule task, target bean " + targetBean + " target method " + targetMethod + ".");
                    }
                } else {
                    ConsoleManager.getSchedulerTaskManager().getScheduleTask()
                            .saveRunningInfo(scheduleKey, ConsoleManager.getSchedulerTaskManager().getScheduleServerUUid(), "bean not exists");
                    LOGGER.debug("Bean name is not exists.");
                }
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * 封装ScheduledMethodRunnable对象
     */
    private static ScheduledMethodRunnable buildScheduledRunnable(String targetBean, String targetMethod, String params, String extKeySuffix, boolean onlyOne) {
        Object bean;
        ScheduledMethodRunnable scheduledMethodRunnable = null;
        try {
            bean = SchedulerTaskManager.getApplicationcontext().getBean(targetBean);
            scheduledMethodRunnable = buildScheduledRunnable(bean, targetMethod, params, extKeySuffix, onlyOne);
        } catch (Exception e) {
            String name = ScheduleUtil.buildScheduleKey(targetBean, targetMethod, extKeySuffix);
            try {
                ConsoleManager.getSchedulerTaskManager().getScheduleTask().saveRunningInfo(name, ConsoleManager.getSchedulerTaskManager().getScheduleServerUUid(), "method is null");
            } catch (Exception e1) {
                LOGGER.debug(e.getLocalizedMessage(), e);
            }
            LOGGER.debug(e.getLocalizedMessage(), e);
        }
        return scheduledMethodRunnable;
    }

    /**
     * 封装ScheduledMethodRunnable对象
     */
    private static ScheduledMethodRunnable buildScheduledRunnable(Object bean, String targetMethod, String params, String extKeySuffix, boolean onlyOne) throws Exception {

        Assert.notNull(bean, "target object must not be null");
        Assert.hasLength(targetMethod, "Method name must not be empty");

        Method method;
        ScheduledMethodRunnable scheduledMethodRunnable;
        Class<?> clazz;
        if (AopUtils.isAopProxy(bean)) {
            clazz = AopProxyUtils.ultimateTargetClass(bean);
        } else {
            clazz = bean.getClass();
        }
        if (params != null) {
            method = ReflectionUtils.findMethod(clazz, targetMethod, String.class);
        } else {
            method = ReflectionUtils.findMethod(clazz, targetMethod);
        }
        Assert.notNull(method, "can not find method named " + targetMethod);
        scheduledMethodRunnable = new ScheduledMethodRunnable(bean, method, params, extKeySuffix, onlyOne);
        return scheduledMethodRunnable;
    }

    /**
     * 检查 动态任务之外的任务类型 是否已经在缓存任务当中
     * 解决删除非动态任务的时候,taskWrapper() 包装的任务仍然会执行的问题
     *
     * @param taskDefine      任务详情
     * @param scheduledFuture 任务回调 可用于关闭任务
     */
    public static void checkTask(TaskDefine taskDefine, ScheduledFuture scheduledFuture) {
        if (taskDefine.getType() != null && TaskDefine.TYPE_SPRING_TASK.equals(taskDefine.getType())) {
            if (!SCHEDULE_FUTURES.containsKey(taskDefine.stringKey())) {
                SCHEDULE_FUTURES.put(taskDefine.stringKey(), scheduledFuture);
                TASKS.put(taskDefine.stringKey(), taskDefine);
            }
        }
    }
}
