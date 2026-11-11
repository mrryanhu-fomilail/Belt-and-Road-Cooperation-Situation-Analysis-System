package com.bri.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 国家元数据实体 — 对应数据库 countries 表，记录沿线国家基本信息。
 *
 * <p>每个国家以 CAMEO 三位码为主键（如 {@code CHN} 表示中国），
 * 携带中英文名称、所属子区域和一带一路标识。
 * 界面显示中文名时通过 {@link com.bri.dao.CountryDao#getByCode} 转换。</p>
 *
 * <h3>JSON 反序列化</h3>
 * <p>Jackson 通过 {@link JsonProperty @JsonProperty} 将 snake_case JSON 字段
 * 映射到 camelCase Java 字段。{@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * 确保 JSON 中新增未知字段时不会抛异常。</p>
 *
 * <h3>使用约定</h3>
 * <ul>
 *   <li>跨模块传递国家信息一律使用 CAMEO 三位码</li>
 *   <li>本类为普通 POJO，线程安全，可跨线程共享</li>
 *   <li>{@code isBri = true} 表示一带一路沿线国家（中国本身为 false）</li>
 * </ul>
 *
 * @author 数据管理模块
 * @version 1.0
 * @see Subregion
 * @see com.bri.dao.CountryDao
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Country {

    /** CAMEO 三位国家码（主键），如 "CHN" */
    @JsonProperty("cameo_code")
    private String cameoCode;

    /** 中文名称，如 "中国" */
    @JsonProperty("name_cn")
    private String nameCn;

    /** 英文名称，如 "China" */
    @JsonProperty("name_en")
    private String nameEn;

    /** 所属子区域键，如 "SEA"，对应 {@link Subregion#SEA} */
    @JsonProperty("subregion")
    private String subregion;

    /** 是否一带一路沿线国家（1 = 是，0 = 否） */
    @JsonProperty("is_bri")
    private int isBri;

    // ==================== 构造方法 ====================

    /** 无参构造，供框架/反序列化使用。 */
    public Country() {}

    /**
     * 全参构造，快速创建国家元数据对象。
     *
     * @param cameoCode CAMEO 三位国家码
     * @param nameCn    中文名
     * @param nameEn    英文名
     * @param subregion 子区域键
     * @param isBri     是否一带一路沿线国（1 = 是）
     */
    public Country(String cameoCode, String nameCn, String nameEn,
                   String subregion, int isBri) {
        this.cameoCode = cameoCode;
        this.nameCn = nameCn;
        this.nameEn = nameEn;
        this.subregion = subregion;
        this.isBri = isBri;
    }

    // ==================== Getters / Setters ====================

    /** @return CAMEO 三位国家码 */
    public String getCameoCode() { return cameoCode; }

    /** @param cameoCode CAMEO 三位国家码 */
    public void setCameoCode(String cameoCode) { this.cameoCode = cameoCode; }

    /** @return 中文名称 */
    public String getNameCn() { return nameCn; }

    /** @param nameCn 中文名称 */
    public void setNameCn(String nameCn) { this.nameCn = nameCn; }

    /** @return 英文名称 */
    public String getNameEn() { return nameEn; }

    /** @param nameEn 英文名称 */
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    /** @return 子区域键 */
    public String getSubregion() { return subregion; }

    /** @param subregion 子区域键 */
    public void setSubregion(String subregion) { this.subregion = subregion; }

    /** @return 是否一带一路沿线国（1/0） */
    public int getIsBri() { return isBri; }

    /** @param isBri 是否一带一路沿线国（1/0） */
    public void setIsBri(int isBri) { this.isBri = isBri; }

    /**
     * 判断是否为一带一路沿线国家。
     *
     * @return {@code true} 如果是沿线国家
     */
    public boolean isBri() {
        return isBri == 1;
    }

    // ==================== Object ====================

    @Override
    public String toString() {
        return "Country{" +
                "code='" + cameoCode + '\'' +
                ", cn='" + nameCn + '\'' +
                ", en='" + nameEn + '\'' +
                ", subregion='" + subregion + '\'' +
                ", bri=" + isBri +
                '}';
    }
}
