/*
 * 版权所有 (c) 2010-2011，小米开源社区 (www.micode.net)
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下位置获得许可证副本：
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解具体语言 governing permissions and limitations.
 */

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 任务列表（TaskList）节点，对应 Google Task 中的“任务列表”分组。
 * 一个 TaskList 包含多个 Task 子节点。
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    private int mIndex;                     // 在远程列表中的位置索引（从1开始）
    private ArrayList<Task> mChildren;      // 子任务列表

    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;                         // 批注：默认索引为1，表示第一个列表
    }

    /**
     * 生成“创建任务列表”的 JSON 动作（用于同步到远端）
     * @param actionId 动作ID（本地生成的唯一标识）
     * @return JSONObject 包含创建动作的所有字段
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type: 指定动作类型为创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id: 本地动作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index: 任务列表在分组中的位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // entity_delta: 实体变更内容
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());          // 列表名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");       // 创建者ID，远端会忽略
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);                  // 实体类型：分组
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 生成“更新任务列表”的 JSON 动作（用于同步到远端）
     * @param actionId 动作ID
     * @return JSONObject 包含更新动作的所有字段
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type: 更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // id: 远端任务的全局唯一标识（GID）
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta: 更新的字段（名称、删除标记）
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 从远端 JSON 对象中解析并设置本地的任务列表属性
     * @param js 远端返回的 JSON 对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // id
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // last_modified: 远端最后修改时间戳（用于冲突检测）
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 从本地数据库的 JSON 表示（META 数据）中设置任务列表属性。
     * 本地存储的笔记结构包含一个头部 JSON 字段，描述了该任务列表对应的本地文件夹。
     * @param js 本地笔记的 JSON 对象（包含 META_HEAD_NOTE 字段）
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 普通文件夹：名称前加 MIUI 前缀
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹：特殊处理默认文件夹和通话记录文件夹
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将当前任务列表的内容生成本地笔记可识别的 JSON 结构（反向转换）
     * @return JSONObject 包含本地笔记的头部信息，或 null 若失败
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            // 移除 MIUI 前缀，得到原始文件夹名
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            folder.put(NoteColumns.SNIPPET, folderName);
            // 根据名称判断是否为系统文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据本地数据库游标（SqlNote）确定同步动作。
     * 比较本地修改标记、本地 sync_id 和远端 last_modified，决定是更新本地、更新远端还是无动作。
     * @param c Cursor 指向一条 SqlNote 记录
     * @return 同步动作常量（定义在 Node 中）：SYNC_ACTION_NONE, UPDATE_LOCAL, UPDATE_REMOTE, ERROR
     */
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 双方最后修改时间一致 => 无需同步
                    return SYNC_ACTION_NONE;
                } else {
                    // 远端有更新 => 覆盖本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 本地有修改
                // 批注：先检查 GID 是否匹配，防止错位
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 仅本地修改 => 推送远端
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 批注：本地和远端都有修改，按策略直接覆盖远端（放弃远端修改）
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    // ---------- 子任务管理方法 ----------
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 添加子任务到末尾
     * @param task 待添加的任务
     * @return 是否成功
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前一个兄弟节点和父节点
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定位置插入子任务
     * @param task  任务
     * @param index 插入位置（0 ~ size）
     * @return 是否成功
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 更新前后兄弟关系
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 移除子任务
     * @param task 要移除的任务
     * @return 是否成功
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 清除兄弟和父节点引用
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新剩余任务的前驱兄弟
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 移动子任务到新位置
     * @param task  待移动的任务
     * @param index 目标索引
     * @return 是否成功
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据远端 GID 查找子任务
     * @param gid 远端唯一标识
     * @return 找到的 Task，否则 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    public void setIndex(int index) {
        this.mIndex = index;
    }

    public int getIndex() {
        return this.mIndex;
    }
}