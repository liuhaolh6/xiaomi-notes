/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import androidx.core.app.NotificationCompat;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 异步执行 Google Tasks 同步任务的 AsyncTask。
 * 在后台执行同步操作，并通过通知栏和回调反馈进度与结果。
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    // 通知的唯一标识 ID，用于更新或取消该通知
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成后的回调接口，用于通知调用方同步已结束（无论成功或失败）。
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    private Context mContext;                       // 上下文，用于获取系统服务和资源
    private NotificationManager mNotifiManager;    // 通知管理器，用于显示状态栏通知
    private GTaskManager mTaskManager;              // Google Tasks 同步管理器，执行实际同步逻辑
    private OnCompleteListener mOnCompleteListener; // 同步完成时的回调监听器

    /**
     * 构造方法。
     * @param context  上下文对象
     * @param listener 同步完成时的回调监听器
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 取消正在进行的同步任务。
     * 实际调用 GTaskManager 的取消方法。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布进度消息，触发 onProgressUpdate 方法。
     * @param message 进度描述文本
     */
    public void publishProgess(String message) {
        publishProgress(new String[] { message });
    }

    /**
     * 显示通知栏消息。
     * @param tickerId 状态栏滚动文本的资源 ID（如 R.string.ticker_syncing）
     * @param content  通知的详细内容文本
     */
    private void showNotification(int tickerId, String content) {
        // 1. 创建 PendingIntent，点击通知后的跳转行为
        PendingIntent pendingIntent;
        if (tickerId != R.string.ticker_success) {
            // 同步未成功时（进行中或失败），点击跳转到设置页面
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesPreferenceActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            // 同步成功时，点击跳转到笔记列表页面
            pendingIntent = PendingIntent.getActivity(mContext, 0,
                    new Intent(mContext, NotesListActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        // 2. 使用 NotificationCompat.Builder 构建通知（兼容所有 Android 版本）
        Notification notification = new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.notification)          // 状态栏小图标
                .setTicker(mContext.getString(tickerId))        // 状态栏滚动文字
                .setWhen(System.currentTimeMillis())            // 通知时间
                .setContentTitle(mContext.getString(R.string.app_name)) // 通知标题
                .setContentText(content)                       // 通知内容
                .setContentIntent(pendingIntent)               // 点击意图
                .setDefaults(Notification.DEFAULT_LIGHTS)       // 闪光灯提示
                .setAutoCancel(true)                            // 用户点击后自动消失
                .build();

        // 发送通知
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 后台执行同步任务（在子线程中运行）。
     * @param unused 无参数
     * @return 同步结果状态码（如 STATE_SUCCESS、STATE_NETWORK_ERROR 等）
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布登录进度提示
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));
        // 调用 GTaskManager 执行实际同步，并返回结果
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新时被调用（由 publishProgress 触发）。
     * @param progress 进度消息数组，取第一个元素作为内容
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        // 显示同步中的通知
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果当前 Context 是 GTaskSyncService，则通过广播发送进度信息
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步结束后在主线程中执行，根据结果显示对应的通知并触发完成回调。
     * @param result doInBackground 返回的状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        // 根据不同的同步结果显示不同的通知
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            // 记录最后一次同步成功的时间
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }
        // 如果有回调监听器，则在新线程中调用回调（避免阻塞 UI 线程）
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}