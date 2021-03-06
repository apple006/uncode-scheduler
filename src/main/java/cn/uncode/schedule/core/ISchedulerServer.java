package cn.uncode.schedule.core;

import java.util.List;

/**
 * 定义服务器基本操作
 *
 * @author liuht
 * @date 2017/10/21 14:19
 */
public interface ISchedulerServer {

    /**
     * 判断指定任务 是否属于指定服务器
     *
     * @param taskName 任务唯一标识
     * @param serverUuid 服务器唯一标识
     * @return 是否属于指定服务器
     */
    boolean isOwner(String taskName, String serverUuid);

    /**
     * 检查本地的定时任务，添加调度器；该功能是检查是否有通过控制台添加uncode task 类型的定时任务，
     * 如果有的话启动该定时任务；这是一种自定义的定时任务类型，任务的启动方式也是自定义的，主要方法在类 DynamicTaskManager 中；
     *
     * @param currentUuid 当前服务器唯一标识
     */
    void checkLocalTask(String currentUuid);

    /**
     * 分配任务
     *
     * @param currentUuid 当前服务器uuid
     * @param taskServerList 所有服务器uuid
     */
    void assignTask(String currentUuid, List<String> taskServerList);

    /**
     * 注册服务器
     *
     * @param server 服务器信息
     */
    void registerScheduleServer(ScheduleServer server);

    /**
     * 判断该服务器是否是分布式调度中心
     *
     * @param serverUuid 服务器唯一标识
     * @param serverList 所有服务器
     * @return 指定服务器是否是分布式调度中心
     */
    boolean isLeader(String serverUuid, List<String> serverList);

    /**
     * 取消注册服务器
     *
     * @param server 服务器信息
     */
    void unRegisterScheduleServer(ScheduleServer server);

    /**
     * 返回所有服务器名称
     *
     * @return 所有服务器名称
     */
    List<String> loadScheduleServerNames();
}
