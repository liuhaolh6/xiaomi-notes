/*
 * 版权所有 (c) 2010-2011，小米开源社区 (www.micode.net)
 *
 * 根据 Apache 许可证 2.0 版（“许可证”）授权；
 * 除非遵守许可证，否则您不得使用此文件。
 * 您可以在以下网址获取许可证副本：
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则按“原样”分发的软件
 * 不附带任何明示或暗示的担保或条件。
 * 请参阅许可证以了解特定语言 governing 权限和限制。
 */

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 元数据类，用于在本地笔记与 Google Tasks 同步时存储关联信息。
 * 继承自 Task，主要保存与 GTask 相关的元数据（如关联的 GTask ID）。
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();

    private String mRelatedGid = null;  // 关联的 Google Tasks ID

    /**
     * 设置元数据内容
     * @param gid 关联的 Google Tasks ID
     * @param metaInfo 已有的元数据 JSON 对象，会向其中添加 GTask ID
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将 GTask ID 存入元数据 JSON 中
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "无法存入关联的 gid");
        }
        setNotes(metaInfo.toString());  // 将整个 JSON 字符串保存为笔记内容
        setName(GTaskStringUtils.META_NOTE_NAME);  // 设置固定名称，标识这是一个元数据节点
    }

    /**
     * 获取关联的 Google Tasks ID
     * @return 关联的 GTask ID，可能为 null
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断当前元数据是否值得保存
     * 只有当笔记内容（getNotes()）不为空时才需要保存
     * @return true 表示值得保存，false 表示不值得保存
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 根据远程服务器返回的 JSON 数据设置当前对象的内容
     * 解析 notes 字段中的 JSON，提取关联的 GTask ID
     * @param js 远程返回的 JSON 对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "无法获取关联的 gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 根据本地 JSON 数据设置内容
     * 此方法不应被调用，因为元数据不应该由本地 JSON 直接构建
     * @param js 本地 JSON 对象
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // 此函数不应被调用
        throw new IllegalAccessError("MetaData:setContentByLocalJSON 不应被调用");
    }

    /**
     * 从当前对象内容生成本地 JSON
     * 此方法不应被调用，因为元数据不需要导出为本地 JSON
     * @return 无返回值，总是抛出异常
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent 不应被调用");
    }

    /**
     * 根据数据库游标判断同步操作类型
     * 此方法不应被调用，因为元数据的同步逻辑由上层处理
     * @param c 数据库游标
     * @return 无返回值，总是抛出异常
     * @throws IllegalAccessError 总是抛出此异常
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction 不应被调用");
    }
}