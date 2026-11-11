package com.bri.dao;

/**
 * 数据访问层异常 — 统一封装数据库操作中的各类异常。
 *
 * <p>DAO 接口的所有方法在遇到 SQL 异常、连接异常等情况时，
 * 均抛出本异常或其子类，而非原始 {@link java.sql.SQLException}，
 * 以保证接口层不泄露底层实现细节（如 JDBC）。</p>
 *
 * @author 数据管理模块
 * @version 1.0
 */
public class DataAccessException extends RuntimeException {

    /**
     * 构造一个包含详细消息的异常。
     *
     * @param message 错误描述
     */
    public DataAccessException(String message) {
        super(message);
    }

    /**
     * 构造一个包含消息和原因的异常。
     *
     * @param message 错误描述
     * @param cause   原始异常
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
