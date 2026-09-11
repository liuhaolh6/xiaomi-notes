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

package net.micode.notes.gtask.exception;

// 动作失败异常：当执行某个操作（如 GTasks 同步）失败时抛出的运行时异常
public class ActionFailureException extends RuntimeException {
    // 序列化版本号，用于反序列化时验证类的兼容性
    private static final long serialVersionUID = 4425249765923293627L;

    // 无参构造器，仅调用父类无参构造器
    public ActionFailureException() {
        super();
    }

    // 带消息的构造器，允许传入异常描述
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    // 带消息和原因的构造器，允许包装底层异常
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}