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
import org.json.JSONObject;

/**
 * 节点抽象类，表示 Google Tasks 中可同步的实体（任务列表或单个任务）。
 * 定义了同步操作的基本字段和抽象方法，具体行为由子类实现。
 *
 * 批注：此模式为“模板方法”模式与“策略”模式的结合，子类实现具体的数据转换和同步决策。
 */
public abstract class Node {
    // 同步动作常量定义
    public static final int SYNC_ACTION_NONE = 0;           // 无需同步
    public static final int SYNC_ACTION_ADD_REMOTE = 1;     // 远程添加（本地新建，推送到远端）
    public static final int SYNC_ACTION_ADD_LOCAL = 2;      // 本地添加（远端新增，拉取到本地）
    public static final int SYNC_ACTION_DEL_REMOTE = 3;     // 删除远程（本地删除，同步到远端）
    public static final int SYNC_ACTION_DEL_LOCAL = 4;      // 删除本地（远端删除，同步到本地）
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;  // 更新远程（本地修改，推送远端）
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;   // 更新本地（远端修改，拉取本地）
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;// 更新冲突（双方均修改，需解决）
    public static final int SYNC_ACTION_ERROR = 8;          // 同步错误（如GID不匹配）

    private String mGid;          // Google Task 全局唯一标识符（远端ID）
    private String mName;         // 节点名称（任务列表名或任务内容）
    private long mLastModified;   // 最后修改时间戳（毫秒，用于冲突检测）
    private boolean mDeleted;     // 删除标记（逻辑删除）

    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    /**
     * 生成“创建”操作的 JSON 数据，用于向远端发送创建请求。
     * @param actionId 本地生成的动作ID，用于关联请求与响应
     * @return JSONObject 包含创建操作所需字段
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成“更新”操作的 JSON 数据，用于向远端发送更新请求。
     * @param actionId 本地生成的动作ID
     * @return JSONObject 包含更新操作所需字段
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 从远端返回的 JSON 对象中解析并设置本节点的内容。
     * @param js 远端JSON（如 GET 或同步响应中的实体数据）
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 从本地数据库的 JSON 表示（如笔记的 meta 字段）中设置本节点内容。
     * @param js 本地存储的 JSON 对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将本节点的内容转换为本地数据库可存储的 JSON 格式。
     * @return JSONObject 用于保存到本地笔记的 meta 字段
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据本地数据库游标（Cursor）确定同步动作。
     * 比较本地修改标记、本地 sync_id 和远端 lastModified，返回对应的 SYNC_ACTION_* 常量。
     * @param c Cursor 指向本地 SqlNote 记录
     * @return 同步动作常量
     */
    public abstract int getSyncAction(Cursor c);

    // ----- 以下为字段的 getter/setter -----
    public void setGid(String gid) {
        this.mGid = gid;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    public String getGid() {
        return this.mGid;
    }

    public String getName() {
        return this.mName;
    }

    public long getLastModified() {
        return this.mLastModified;
    }

    public boolean getDeleted() {
        return this.mDeleted;
    }
}