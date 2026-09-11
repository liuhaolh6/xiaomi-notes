/*
 * 版权所有 (c) 2010-2011，MiCode 开源社区 (www.micode.net)
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获得许可证副本：
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 不附带任何明示或暗示的担保或条件。
 * 请参阅许可证了解具体语言管理权限和限制。
 */

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Tasks 同步服务
 * 用于在后台启动或取消与 Google Tasks 的同步任务。
 */
public class GTaskSyncService extends Service {
    // 广播中携带动作类型的键名
    public final static String ACTION_STRING_NAME = "sync_action_type";

    // 动作类型：开始同步
    public final static int ACTION_START_SYNC = 0;

    // 动作类型：取消同步
    public final static int ACTION_CANCEL_SYNC = 1;

    // 无效动作
    public final static int ACTION_INVALID = 2;

    // 广播名称，用于发送同步状态变化
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    // 广播中是否正在同步的布尔值键名
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    // 广播中同步进度消息的键名
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    private static GTaskASyncTask mSyncTask = null;   // 当前正在执行的同步任务
    private static String mSyncProgress = "";         // 当前同步进度文本

    /**
     * 开始同步（在服务内部调用）
     * 如果还没有同步任务，则创建并执行。
     */
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();   // 同步完成后停止服务
                }
            });
            sendBroadcast("");
            mSyncTask.execute();
        }
    }

    /**
     * 取消正在进行的同步
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;   // 服务被杀死后会自动重启，并保留 Intent
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();   // 内存不足时取消同步
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;   // 该服务不支持绑定，仅用于启动
    }

    /**
     * 发送广播通知当前同步状态和进度。
     * @param msg 进度消息文本
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    /**
     * 启动同步（由 Activity 调用）
     * @param activity 发起同步的 Activity，用于设置 GTaskManager 的上下文
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 取消同步（可由任意 Context 调用）
     * @param context 上下文
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 查询是否正在同步
     * @return true 表示正在同步
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取当前同步进度文本
     * @return 进度消息
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}