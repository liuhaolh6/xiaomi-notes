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

// 网络失败异常：当网络请求或连接出现问题时抛出的受检异常
public class NetworkFailureException extends Exception {
    // 序列化版本号，用于反序列化时的版本兼容性验证
    private static final long serialVersionUID = 2107610287180234136L;

    // 无参构造器
    public NetworkFailureException() {
        super();
    }

    // 带消息的构造器
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    // 带消息和原因的构造器（支持异常链）
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}