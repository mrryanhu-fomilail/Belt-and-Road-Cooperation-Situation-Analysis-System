package com.bri.model;

/**
 * 子区域枚举 — 对应 subregions 字典表中的 key。
 *
 * <p>每个枚举值对应 subregions 字典表中的 key，同时携带中文名和英文名，
 * 方便界面展示与国际化。枚举值命名与数据库存储的 key 完全一致。</p>
 *
 * <h3>子区域一览</h3>
 * <ul>
 *   <li>{@link #EA} — 东亚 (East Asia)</li>
 *   <li>{@link #SEA} — 东南亚 (Southeast Asia)</li>
 *   <li>{@link #CA} — 中亚 (Central Asia)</li>
 *   <li>{@link #SA} — 南亚 (South Asia)</li>
 *   <li>{@link #ME} — 中东 (Middle East)</li>
 *   <li>{@link #EU} — 欧洲 (Europe)</li>
 *   <li>{@link #AF} — 非洲 (Africa)</li>
 *   <li>{@link #NAM} — 北美洲 (North America)</li>
 *   <li>{@link #SAM} — 南美洲 (South America)</li>
 *   <li>{@link #OCE} — 大洋洲 (Oceania)</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see com.bri.model.Country
 */
public enum Subregion {

    /** 东亚 */
    EA("东亚", "East Asia"),

    /** 东南亚 */
    SEA("东南亚", "Southeast Asia"),

    /** 中亚 */
    CA("中亚", "Central Asia"),

    /** 南亚 */
    SA("南亚", "South Asia"),

    /** 中东 */
    ME("中东", "Middle East"),

    /** 欧洲 */
    EU("欧洲", "Europe"),

    /** 非洲 */
    AF("非洲", "Africa"),

    /** 北美洲 */
    NAM("北美洲", "North America"),

    /** 南美洲 */
    SAM("南美洲", "South America"),

    /** 大洋洲 */
    OCE("大洋洲", "Oceania");

    /** 子区域中文名 */
    private final String nameCn;

    /** 子区域英文名 */
    private final String nameEn;

    /**
     * 构造子区域枚举常量。
     *
     * @param nameCn 中文名称
     * @param nameEn 英文名称
     */
    Subregion(String nameCn, String nameEn) {
        this.nameCn = nameCn;
        this.nameEn = nameEn;
    }

    /**
     * 返回子区域中文名。
     *
     * @return 中文名，如"东南亚"
     */
    public String getNameCn() {
        return nameCn;
    }

    /**
     * 返回子区域英文名。
     *
     * @return 英文名，如 "Southeast Asia"
     */
    public String getNameEn() {
        return nameEn;
    }

    /**
     * 根据数据库存储的 key 查找对应的枚举值。
     *
     * @param key 子区域键，如 "SEA"、 "ME"
     * @return 匹配的 Subregion 枚举值；若 key 为 null 或无匹配，返回 {@code null}
     */
    public static Subregion fromKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return 子区域 key，如 "SEA"
     */
    @Override
    public String toString() {
        return name();
    }
}

