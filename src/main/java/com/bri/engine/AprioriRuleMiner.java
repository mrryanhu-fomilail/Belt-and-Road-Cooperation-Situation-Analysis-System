package com.bri.engine;

import com.bri.model.AssociationRule;
import com.bri.model.Event;
import com.bri.model.Country;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Apriori 关联规则挖掘器 — 分析"当 A 国与 B 国发生 X 类事件后，
 * 后续倾向于发生 Y 类事件"的模式。
 *
 * <h3>核心思路</h3>
 * <ol>
 *   <li><b>事务构建</b>：将同一个国家对（A-B）在同一月份内发生的事件代码集合视为一个"事务"。</li>
 *   <li><b>频繁项集</b>：使用 Apriori 算法迭代寻找支持度 ≥ minSupport 的项集。</li>
 *   <li><b>规则生成</b>：从频繁项集生成置信度 ≥ minConfidence 的关联规则。</li>
 * </ol>
 *
 * <h3>事务格式</h3>
 * 每个事务是一个事件代码集合，如 {"05", "07", "10"} 表示当月发生了经贸合作、基础设施援助和外交沟通。
 * 事务以国家对+年月为键，例如 "CHN-PAK#2026-06"。
 */
public class AprioriRuleMiner {

    private final double minSupport;
    private final double minConfidence;
    private final int maxItemsetSize;

    /** CAMEO 事件大类代码到中文描述的映射 */
    private static final Map<String, String> ROOT_DESC = new LinkedHashMap<>();
    static {
        ROOT_DESC.put("01", "媒体声明");
        ROOT_DESC.put("02", "外交沟通");
        ROOT_DESC.put("03", "外交合作");
        ROOT_DESC.put("04", "外交施压");
        ROOT_DESC.put("05", "经贸合作");
        ROOT_DESC.put("06", "基础设施援助");
        ROOT_DESC.put("07", "军事合作");
        ROOT_DESC.put("08", "军事冲突");
        ROOT_DESC.put("09", "领土争端");
        ROOT_DESC.put("10", "制裁封锁");
        ROOT_DESC.put("11", "抗议示威");
        ROOT_DESC.put("12", "政治合作");
        ROOT_DESC.put("13", "人道援助");
        ROOT_DESC.put("14", "文化科技交流");
        ROOT_DESC.put("15", "资源能源合作");
        ROOT_DESC.put("16", "金融投资");
        ROOT_DESC.put("17", "交通物流");
        ROOT_DESC.put("18", "安全合作");
        ROOT_DESC.put("19", "边境管理");
        ROOT_DESC.put("20", "其他事件");
    }

    /**
     * @param minSupport      最小支持度阈值（0~1），建议 0.01~0.05
     * @param minConfidence   最小置信度阈值（0~1），建议 0.5~0.7
     * @param maxItemsetSize  最大项集大小，防止组合爆炸
     */
    public AprioriRuleMiner(double minSupport, double minConfidence, int maxItemsetSize) {
        this.minSupport = minSupport;
        this.minConfidence = minConfidence;
        this.maxItemsetSize = maxItemsetSize;
    }

    /**
     * 执行关联规则挖掘。
     *
     * @param events      所有事件列表（引擎已加载的原始事件）
     * @param countries   一带一路国家列表（用于名称映射）
     * @return 满足阈值的关联规则列表（按提升度降序排列）
     */
    public List<AssociationRule> mine(List<Event> events, List<Country> countries) {
        if (events == null || events.isEmpty()) return Collections.emptyList();

        // 1. 构建事务：{国家对#年月 → 事件代码集合}
        Map<String, Set<String>> transactions = buildTransactions(events);
        if (transactions.isEmpty()) return Collections.emptyList();

        int totalTransactions = transactions.size();

        // 2. 生成频繁项集
        List<Set<String>> frequentItemsets = apriori(transactions, totalTransactions);

        // 3. 从频繁项集生成关联规则
        List<AssociationRule> rules = generateRules(frequentItemsets, transactions, totalTransactions);

        // 4. 按提升度降序排列
        rules.sort((a, b) -> Double.compare(b.getLift(), a.getLift()));

        // 5. 对规则的前项和后项添加描述性文字
        enrichDescriptions(rules);

        return rules;
    }

    // ==================== 事务构建 ====================

    /**
     * 将事件列表按"国家对#年月"分组，提取每组的事件代码集合作为事务。
     * 只保留双边事件（actor1 和 actor2 都非空）且事件代码有效的事件。
     */
    Map<String, Set<String>> buildTransactions(List<Event> events) {
        Map<String, Set<String>> transactions = new HashMap<>();

        for (Event e : events) {
            if (e.getActor1Code() == null || e.getActor2Code() == null) continue;
            if (e.getActor1Code().isEmpty() || e.getActor2Code().isEmpty()) continue;
            if (e.getEventRoot() == null || e.getEventRoot().isEmpty()) continue;
            if (e.getEventDate() == null || e.getEventDate().length() < 7) continue;

            // 统一 actor1 < actor2 以保证国家对唯一性
            String a1 = e.getActor1Code();
            String a2 = e.getActor2Code();
            String pairKey;
            if (a1.compareTo(a2) <= 0) {
                pairKey = a1 + "-" + a2;
            } else {
                pairKey = a2 + "-" + a1;
            }

            String month = e.getEventDate().substring(0, 7);
            String transactionKey = pairKey + "#" + month;

            transactions.computeIfAbsent(transactionKey, k -> new HashSet<>())
                        .add(e.getEventRoot());
        }

        return transactions;
    }

    // ==================== Apriori 算法核心 ====================

    /**
     * Apriori 算法 — 迭代生成频繁项集。
     */
    private List<Set<String>> apriori(Map<String, Set<String>> transactions, int total) {
        List<Set<String>> result = new ArrayList<>();

        // 1. 生成频繁 1-项集（单个事件类型）
        Map<String, Integer> itemCount = new HashMap<>();
        for (Set<String> t : transactions.values()) {
            for (String item : t) {
                itemCount.merge(item, 1, Integer::sum);
            }
        }

        List<String> frequentItems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : itemCount.entrySet()) {
            double support = (double) e.getValue() / total;
            if (support >= minSupport) {
                frequentItems.add(e.getKey());
                Set<String> itemset = new HashSet<>();
                itemset.add(e.getKey());
                result.add(itemset);
            }
        }

        // 没有足够频繁项时提前返回
        if (frequentItems.size() < 2) return result;

        // 2. 迭代生成 k-项集 (k ≥ 2)
        List<Set<String>> currentLevel = new ArrayList<>();
        for (int i = 0; i < frequentItems.size(); i++) {
            for (int j = i + 1; j < frequentItems.size(); j++) {
                Set<String> pair = new HashSet<>();
                pair.add(frequentItems.get(i));
                pair.add(frequentItems.get(j));
                currentLevel.add(pair);
            }
        }

        int k = 2;
        while (!currentLevel.isEmpty() && k <= maxItemsetSize) {
            List<Set<String>> nextLevel = new ArrayList<>();

            // 计算当前候选集的支持度
            for (Set<String> candidate : currentLevel) {
                int count = 0;
                for (Set<String> t : transactions.values()) {
                    if (t.containsAll(candidate)) count++;
                }
                double support = (double) count / total;
                if (support >= minSupport) {
                    result.add(candidate);
                    nextLevel.add(candidate);
                }
            }

            // 生成下一级候选集 (k+1)-项集
            if (k < maxItemsetSize) {
                currentLevel = generateCandidates(nextLevel, frequentItems);
            } else {
                currentLevel = Collections.emptyList();
            }
            k++;
        }

        return result;
    }

    /**
     * 从当前频繁 k-项集生成候选 (k+1)-项集。
     * 采用 F_k-1 × F_k-1 连接策略：两个 k-项集前 k-1 项相同才连接。
     */
    private List<Set<String>> generateCandidates(List<Set<String>> frequentKItemsets,
                                                  List<String> allFrequentItems) {
        // 如果当前频繁项集太少，直接用单个频繁项集组合生成
        if (frequentKItemsets.size() < 2) {
            // 尝试直接组合
            if (frequentKItemsets.size() == 1) {
                Set<String> base = frequentKItemsets.get(0);
                List<Set<String>> candidates = new ArrayList<>();
                for (String item : allFrequentItems) {
                    if (!base.contains(item)) {
                        Set<String> newSet = new HashSet<>(base);
                        newSet.add(item);
                        candidates.add(newSet);
                    }
                }
                return candidates;
            }
            return Collections.emptyList();
        }

        // 标准连接：两个 k-项集前 k-1 项相同
        List<Set<String>> candidates = new ArrayList<>();
        int k = frequentKItemsets.get(0).size();

        for (int i = 0; i < frequentKItemsets.size(); i++) {
            for (int j = i + 1; j < frequentKItemsets.size(); j++) {
                Set<String> a = frequentKItemsets.get(i);
                Set<String> b = frequentKItemsets.get(j);

                // 检查前 k-1 项是否相同
                List<String> sortedA = new ArrayList<>(a);
                List<String> sortedB = new ArrayList<>(b);
                Collections.sort(sortedA);
                Collections.sort(sortedB);

                boolean joinable = true;
                for (int idx = 0; idx < k - 1; idx++) {
                    if (!sortedA.get(idx).equals(sortedB.get(idx))) {
                        joinable = false;
                        break;
                    }
                }

                if (joinable) {
                    Set<String> newSet = new HashSet<>(a);
                    newSet.addAll(b);
                    if (newSet.size() == k + 1) {
                        candidates.add(newSet);
                    }
                }
            }
        }
        return candidates;
    }

    // ==================== 规则生成 ====================

    /**
     * 从频繁项集生成关联规则。
     * 对每个大小 ≥ 2 的频繁项集，枚举其所有可能的前项（真子集），
     * 计算置信度，过滤出满足 minConfidence 的规则。
     */
    private List<AssociationRule> generateRules(List<Set<String>> frequentItemsets,
                                                 Map<String, Set<String>> transactions,
                                                 int total) {
        List<AssociationRule> rules = new ArrayList<>();

        // 计算每个事件类型的出现次数（用于提升度计算）
        Map<String, Integer> itemCount = new HashMap<>();
        for (Set<String> t : transactions.values()) {
            for (String item : t) {
                itemCount.merge(item, 1, Integer::sum);
            }
        }

        for (Set<String> itemset : frequentItemsets) {
            if (itemset.size() < 2) continue;

            // 生成所有真子集作为前项候选项
            List<String> itemList = new ArrayList<>(itemset);
            List<Set<String>> subsets = generateSubsets(itemList);

            // 计算整个项集的支持度计数
            int itemsetCount = countSupport(itemset, transactions);

            for (Set<String> antecedent : subsets) {
                if (antecedent.isEmpty() || antecedent.size() == itemset.size()) continue;

                // 后项 = 项集 - 前项
                Set<String> consequent = new HashSet<>(itemset);
                consequent.removeAll(antecedent);
                if (consequent.isEmpty()) continue;

                // 计算前项支持度计数
                int antecedentCount = countSupport(antecedent, transactions);

                // 计算置信度
                double confidence = (double) itemsetCount / antecedentCount;
                if (confidence < minConfidence) continue;

                // 计算支持度
                double support = (double) itemsetCount / total;

                // 计算覆盖度
                double coverage = (double) antecedentCount / total;

                // 计算后项先验概率
                double consequentProb = 0;
                for (String item : consequent) {
                    Integer cnt = itemCount.get(item);
                    if (cnt != null) {
                        consequentProb = Math.max(consequentProb, (double) cnt / total);
                    }
                }
                if (consequentProb < 1e-9) continue;

                // 计算提升度
                double lift = confidence / consequentProb;

                AssociationRule rule = new AssociationRule(
                    formatItemset(antecedent),
                    formatItemset(consequent),
                    support, confidence, lift, coverage
                );
                rules.add(rule);
            }
        }

        return rules;
    }

    /**
     * 生成列表的所有非空真子集。
     */
    private List<Set<String>> generateSubsets(List<String> items) {
        List<Set<String>> subsets = new ArrayList<>();
        int n = items.size();
        // 从 1 到 2^n - 2 (排除空集和全集)
        for (int mask = 1; mask < (1 << n) - 1; mask++) {
            Set<String> subset = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(items.get(i));
                }
            }
            subsets.add(subset);
        }
        return subsets;
    }

    /**
     * 计算一个项集在事务中的支持度计数。
     */
    private int countSupport(Set<String> itemset, Map<String, Set<String>> transactions) {
        int count = 0;
        for (Set<String> t : transactions.values()) {
            if (t.containsAll(itemset)) count++;
        }
        return count;
    }

    // ==================== 格式化 ====================

    /**
     * 将事件代码集合格式化为可读字符串。
     */
    private String formatItemset(Set<String> itemset) {
        if (itemset == null || itemset.isEmpty()) return "∅";
        List<String> sorted = new ArrayList<>(itemset);
        Collections.sort(sorted);
        return sorted.stream()
                .map(code -> {
                    String desc = ROOT_DESC.getOrDefault(code, "事件" + code);
                    return desc + " (" + code + ")";
                })
                .collect(Collectors.joining(" + "));
    }

    /**
     * 为规则的前项和后项补充国家对描述。
     */
    private void enrichDescriptions(List<AssociationRule> rules) {
        // 已由 formatItemset 完成，不需要额外处理
    }

    /**
     * 获取事件大类码对应的中文描述。
     */
    public static String getRootDescription(String code) {
        return ROOT_DESC.getOrDefault(code, "未知事件");
    }

    /**
     * 获取所有事件大类描述。
     */
    public static Map<String, String> getAllRootDescriptions() {
        return Collections.unmodifiableMap(ROOT_DESC);
    }
}
