package com.bri.model;

/**
 * 关联规则 — Apriori 挖掘结果。
 *
 * <p>表示"当 A 国与 B 国发生 X 类事件后，后续倾向于发生 Y 类事件"的规则。
 * 例如：{CHN→PAK: 经济合作} → {CHN→PAK: 基础设施援助}</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>antecedent（前项/条件）：触发事件描述，如 "CHN→PAK: 经贸合作 (05)"</li>
 *   <li>consequent（后项/结论）：被触发事件描述，如 "CHN→PAK: 基础设施援助 (07)"</li>
 *   <li>support（支持度）：同时包含前项和后项的事务占总事务的比例</li>
 *   <li>confidence（置信度）：前项出现时后项也出现的条件概率</li>
 *   <li>lift（提升度）：置信度与后项先验概率的比值，>1 表示正相关</li>
 *   <li>coverage（覆盖度）：前项出现的事务占总事务的比例</li>
 * </ul>
 */
public class AssociationRule {

    private String antecedent;
    private String consequent;
    private double support;
    private double confidence;
    private double lift;
    private double coverage;

    public AssociationRule() {}

    public AssociationRule(String antecedent, String consequent,
                           double support, double confidence, double lift, double coverage) {
        this.antecedent = antecedent;
        this.consequent = consequent;
        this.support = support;
        this.confidence = confidence;
        this.lift = lift;
        this.coverage = coverage;
    }

    // ==================== Getters / Setters ====================

    public String getAntecedent() { return antecedent; }
    public void setAntecedent(String antecedent) { this.antecedent = antecedent; }

    public String getConsequent() { return consequent; }
    public void setConsequent(String consequent) { this.consequent = consequent; }

    public double getSupport() { return support; }
    public void setSupport(double support) { this.support = support; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public double getLift() { return lift; }
    public void setLift(double lift) { this.lift = lift; }

    public double getCoverage() { return coverage; }
    public void setCoverage(double coverage) { this.coverage = coverage; }

    @Override
    public String toString() {
        return String.format("%s → %s (support=%.3f, confidence=%.3f, lift=%.3f)",
                antecedent, consequent, support, confidence, lift);
    }
}
