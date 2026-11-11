package com.bri.model;

/**
 * 事件分类枚举 — 描述 GDELT 事件属于合作、冲突还是其他类型。
 *
 * <p>分类依据 CAMEO EventRootCode：
 * {@code 04-06} 为合作类事件 (COOP)，
 * {@code 08-14} 为冲突类事件 (CONFLICT)，
 * {@code 其余为其他} (OTHER)，
 * 在数据导入阶段写入 events 表的 {@code is_coop} 字段，
 * 下游模块直接读取该字段而无需自行判定。</p>
 *
 * <h3>数据库映射</h3>
 * <table>
 *   <tr><th>枚举值</th><th>数据库 is_coop</th><th>含义</th></tr>
 *   <tr><td>{@link #COOP}</td><td>1</td><td>合作事件</td></tr>
 *   <tr><td>{@link #CONFLICT}</td><td>0</td><td>冲突事件</td></tr>
 *   <tr><td>{@link #OTHER}</td><td>-1</td><td>其他/中性事件</td></tr>
 * </table>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see com.bri.model.Event#isCoop
 */
public enum EventCategory {

    /** 合作事件 — EventRootCode 04-06 */
    COOP(1, "合作事件"),

    /** 冲突事件 — EventRootCode 08-14 */
    CONFLICT(0, "冲突事件"),

    /** 其他/中性事件 */
    OTHER(-1, "其他");

    /** 数据库存储的整型值 */
    private final int dbValue;

    /** 中文描述 */
    private final String description;

    /**
     * 构造事件分类枚举常量。
     *
     * @param dbValue     数据库中 is_coop 列的整型值
     * @param description 中文描述
     */
    EventCategory(int dbValue, String description) {
        this.dbValue = dbValue;
        this.description = description;
    }

    /**
     * 返回数据库中 is_coop 列的对应值。
     *
     * @return 1（合作）、0（冲突）或 -1（其他）
     */
    public int getDbValue() {
        return dbValue;
    }

    /**
     * 返回分类的中文描述。
     *
     * @return 中文描述，如"合作事件"
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据数据库 is_coop 值反查枚举。
     *
     * @param dbValue 数据库 is_coop 列的值
     * @return 匹配的 EventCategory；无匹配返回 OTHER
     */
    public static EventCategory fromDbValue(int dbValue) {
        for (EventCategory c : values()) {
            if (c.dbValue == dbValue) {
                return c;
            }
        }
        return OTHER;
    }

    /**
     * 根据 CAMEO EventRootCode 判定事件分类。
     *
     * @param eventRoot 事件大类编码（两位字符串，如 "04"、 "10"）
     * @return 对应的 EventCategory
     * @throws NumberFormatException 如果 eventRoot 无法解析为整数
     */
    public static EventCategory fromEventRoot(String eventRoot) {
        if (eventRoot == null || eventRoot.trim().isEmpty()) {
            return OTHER;
        }
        try {
            int code = Integer.parseInt(eventRoot.trim());
            if (code >= 4 && code <= 6) {
                return COOP;
            }
            if (code >= 8 && code <= 14) {
                return CONFLICT;
            }
        } catch (NumberFormatException e) {
            // 非数字 eventRoot 视为 OTHER
        }
        return OTHER;
    }

    /**
     * {@inheritDoc}
     *
     * @return 中文描述
     */
    @Override
    public String toString() {
        return description;
    }
}
