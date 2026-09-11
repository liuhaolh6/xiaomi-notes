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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Google Tasks 同步管理器（单例）
 * 负责本地笔记数据库与 Google Tasks 服务之间的双向同步。
 */
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    // 同步结果状态码
    public static final int STATE_SUCCESS = 0;              // 成功
    public static final int STATE_NETWORK_ERROR = 1;        // 网络错误
    public static final int STATE_INTERNAL_ERROR = 2;       // 内部错误
    public static final int STATE_SYNC_IN_PROGRESS = 3;     // 同步进行中
    public static final int STATE_SYNC_CANCELLED = 4;       // 同步已取消

    private static GTaskManager mInstance = null;           // 单例实例

    private Activity mActivity;                             // 用于获取授权令牌的 Activity
    private Context mContext;                               // 上下文
    private ContentResolver mContentResolver;               // 内容解析器

    private boolean mSyncing;                               // 是否正在同步
    private boolean mCancelled;                             // 是否已取消

    // 映射表：存储从服务端拉取的任务列表、任务节点、元数据
    private HashMap<String, TaskList> mGTaskListHashMap;    // gid -> TaskList
    private HashMap<String, Node> mGTaskHashMap;            // gid -> Node (Task 或 TaskList)
    private HashMap<String, MetaData> mMetaHashMap;         // 关联 gid -> MetaData

    private TaskList mMetaList;                             // 存放元数据（笔记内容）的特殊任务列表
    private HashSet<Long> mLocalDeleteIdMap;                // 本地已删除的笔记 ID 集合
    private HashMap<String, Long> mGidToNid;                // 远程 gid -> 本地笔记 id
    private HashMap<Long, String> mNidToGid;                // 本地笔记 id -> 远程 gid

    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    /** 获取单例实例 */
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    /** 设置 Activity 上下文（用于获取授权令牌） */
    public synchronized void setActivityContext(Activity activity) {
        mActivity = activity;
    }

    /**
     * 执行同步操作。
     * @param context    上下文
     * @param asyncTask  用于发布进度的异步任务
     * @return 同步结果状态码
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        if (mSyncing) {
            Log.d(TAG, "同步正在进行中");
            return STATE_SYNC_IN_PROGRESS;
        }
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        // 清空所有缓存映射
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // 登录 Google Tasks
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("登录 Google Tasks 失败");
                }
            }

            // 从服务端获取任务列表
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // 执行内容同步
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 清理资源
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    /**
     * 初始化任务列表：从服务端拉取所有任务列表及任务，并构建元数据列表。
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            JSONArray jsTaskLists = client.getTaskLists();

            // 首先初始化元数据列表（特殊文件夹，用于存储笔记的额外信息）
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // 加载元数据子任务
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // 如果元数据列表不存在，则创建它
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // 初始化普通任务列表（排除元数据文件夹）
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // 加载该任务列表下的所有任务
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: 处理 JSONObject 失败");
        }
    }

    /**
     * 同步主要内容：处理本地删除、文件夹、笔记的增删改查。
     */
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;
        Node node;

        mLocalDeleteIdMap.clear();

        if (mCancelled) {
            return;
        }

        // 1. 处理本地已删除的笔记（在回收站中）
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "查询回收站文件夹失败");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 2. 先同步文件夹（任务列表）
        syncFolder();

        // 3. 处理本地存在的普通笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程已删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "查询数据库中的现有笔记失败");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 4. 处理剩余的远程节点（本地没有对应项，需新增到本地）
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // 5. 批量删除本地已标记删除的笔记
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("批量删除本地已删除笔记失败");
            }
        }

        // 6. 刷新本地同步 ID（将服务端的最新修改时间写回本地）
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }
    }

    /**
     * 同步文件夹（任务列表）：包括根文件夹、通话记录文件夹和普通用户文件夹。
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // 根文件夹（默认文件夹）
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 对于系统文件夹，仅在名称不一致时更新远程名称
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "查询根文件夹失败");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 通话记录文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "查询通话记录文件夹失败");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 本地现有的普通文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "查询现有文件夹失败");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // 处理远程新增的文件夹（本地尚未创建）
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    /**
     * 根据同步类型执行具体的同步操作。
     * @param syncType 同步类型（ADD_LOCAL, ADD_REMOTE, DEL_LOCAL, DEL_REMOTE, UPDATE_LOCAL, UPDATE_REMOTE, UPDATE_CONFLICT, NONE, ERROR）
     * @param node     远程节点（可能为 null）
     * @param c        本地数据库游标（可能为 null）
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // 合并双方的修改可能是个好主意，目前简单地采用本地更新远程
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                break;
            case Node.SYNC_ACTION_ERROR:
            default:
                throw new ActionFailureException("未知的同步动作类型");
        }
    }

    /** 将远程节点添加到本地数据库 */
    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote;
        if (node instanceof TaskList) {
            // 如果是系统预定义文件夹，使用已有 ID；否则创建新文件夹
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else { // Task
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                // 如果 JSON 中包含笔记 ID，且该 ID 在本地已存在，则移除 ID 以强制创建新记录
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                data.remove(DataColumns.ID);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "无法在本地找到任务的父文件夹 ID");
                throw new ActionFailureException("无法添加本地节点");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);          // 插入或更新
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /** 用远程节点的内容更新本地节点 */
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "无法在本地找到任务的父文件夹 ID");
            throw new ActionFailureException("无法更新本地节点");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);           // 强制更新
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    /** 将本地节点添加到远程服务端 */
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "无法找到任务的父任务列表");
                throw new ActionFailureException("无法添加远程任务");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);
            GTaskClient.getInstance().createTask(task);
            n = task;
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            TaskList tasklist = null;
            // 构建文件夹名称：前缀 + 本地片段（或系统预定义名称）
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            // 检查是否已存在同名文件夹
            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();
                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = tasklist;
        }

        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    /** 用本地节点的内容更新远程节点 */
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);
        updateRemoteMeta(node.getGid(), sqlNote);

        // 如果是任务（笔记）且父文件夹发生变化，需要移动任务
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();
            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "无法找到任务的父任务列表");
                throw new ActionFailureException("无法更新远程任务");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);
            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    /** 更新或创建与笔记关联的元数据（MetaData） */
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    /** 刷新本地笔记的同步 ID（sync_id），使其与服务端的最新修改时间保持一致 */
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 重新获取最新的 GTasks 数据
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "某些本地项在同步后丢失了 gid");
                        throw new ActionFailureException("同步后部分本地项没有对应的 gid");
                    }
                }
            } else {
                Log.w(TAG, "查询本地笔记以刷新同步 ID 失败");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    /** 获取当前同步账号名称 */
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    /** 取消正在进行的同步 */
    public void cancelSync() {
        mCancelled = true;
    }
}