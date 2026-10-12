package com.volunteer.common.storage;

import java.io.InputStream;

/**
 * 对象存储统一抽象。
 *
 * <h2>设计目的</h2>
 * <p>业务模块（如 {@code volunteer-report} 的异步导出）需要把生成的文件写入对象存储，
 * 但不应直接依赖 {@code volunteer-server} 模块（会形成反向依赖）。
 * 因此在 {@code volunteer-common} 中定义本接口并提供 MinIO 实现，业务模块仅依赖本接口。
 *
 * <h2>为什么需要它</h2>
 * <p>异步导出任务原本把产物写入 {@code java.io.tmpdir/volunteer-exports}，带来三个问题：
 * <ol>
 *   <li>多副本部署时，任务在 A 实例生成文件，用户请求可能被负载均衡到 B 实例，下载 404；</li>
 *   <li>容器重启或重建后文件丢失；</li>
 *   <li>本地磁盘无配额管理，长期运行会占满磁盘。</li>
 * </ol>
 * <p>改为对象存储后，产物由所有实例共享。
 *
 * @author Volunteer Platform Team
 */
public interface FileStorage {

    /** 用户上传文件的存储前缀（受白名单校验约束）。 */
    String PREFIX_USER_UPLOAD = "upload";

    /** 系统导出产物的存储前缀。 */
    String PREFIX_EXPORT = "export";

    /**
     * 上传字节数组。
     *
     * @param storageKey  存储前缀，用于按用途隔离对象，取值见本接口常量
     * @param objectName  对象名（不含前缀）
     * @param content     文件内容
     * @param contentType MIME 类型
     * @return 含前缀的完整对象名，调用方需保存该值以便后续下载/删除
     */
    String upload(String storageKey, String objectName, byte[] content, String contentType);

    /**
     * 上传输入流。实现方负责关闭流。
     *
     * @param storageKey  存储前缀
     * @param objectName  对象名（不含前缀）
     * @param stream      输入流
     * @param size        字节数；未知时传 {@code -1}
     * @param contentType MIME 类型
     * @return 含前缀的完整对象名
     */
    String upload(String storageKey, String objectName, InputStream stream, long size, String contentType);

    /**
     * 下载对象内容。
     *
     * @param fullObjectName 含前缀的完整对象名
     * @return 文件字节内容
     * @throws com.volunteer.common.exception.BusinessException 对象不存在或读取失败
     */
    byte[] download(String fullObjectName);

    /**
     * 下载对象为输入流。调用方负责关闭。
     *
     * @param fullObjectName 含前缀的完整对象名
     * @return 输入流
     */
    InputStream openStream(String fullObjectName);

    /**
     * 删除对象。对象不存在时不应抛异常。
     *
     * @param fullObjectName 含前缀的完整对象名
     */
    void delete(String fullObjectName);

    /**
     * 判断对象是否存在。
     *
     * @param fullObjectName 含前缀的完整对象名
     * @return 存在返回 true
     */
    boolean exists(String fullObjectName);
}
