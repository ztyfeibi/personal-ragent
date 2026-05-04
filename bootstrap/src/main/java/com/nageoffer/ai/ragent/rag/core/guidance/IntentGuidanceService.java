/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.guidance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.config.GuidanceProperties;
import com.nageoffer.ai.ragent.rag.constant.RAGConstant;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNodeRegistry;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScoreFilters;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 意图澄清服务。
 * 用于在检索到多个高相似度候选意图时，判断是否需要向用户发起进一步确认。
 */
@Service
@RequiredArgsConstructor
public class IntentGuidanceService {

    private final GuidanceProperties guidanceProperties;
    private final IntentNodeRegistry intentNodeRegistry;
    private final PromptTemplateLoader promptTemplateLoader;

    /**
     * 检测当前问题是否存在需要进一步澄清的意图歧义。
     * 仅当引导功能开启、候选意图中存在得分接近的多系统歧义，
     * 且用户问题中没有明确提到具体系统时，才返回澄清提示词。
     */
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        // 总开关关闭时，直接跳过歧义引导。
        if (!Boolean.TRUE.equals(guidanceProperties.getEnabled())) {
            return GuidanceDecision.none();
        }

        // 找出需要澄清的歧义分组；没有有效歧义时不触发引导。
        AmbiguityGroup group = findAmbiguityGroup(subIntents);
        if (group == null || CollUtil.isEmpty(group.optionIds())) {
            return GuidanceDecision.none();
        }

        // 如果用户已经在问题中明确提到了目标系统，则无需再次追问。
        List<String> systemNames = resolveOptionNames(group.optionIds());
        if (shouldSkipGuidance(question, systemNames)) {
            return GuidanceDecision.none();
        }

        // 根据歧义主题和候选系统生成澄清提示词，交给上层引导用户选择。
        String prompt = buildPrompt(group.topicName(), group.optionIds());
        return GuidanceDecision.prompt(prompt);
    }

    /**
     * 从单个子问题的候选意图中识别最值得发起澄清的歧义分组。
     * 只有当同一主题下存在多个得分接近且归属不同系统的候选时，才认为存在有效歧义。
     */
    private AmbiguityGroup findAmbiguityGroup(List<SubQuestionIntent> subIntents) {
        // 当前仅对单个子问题做澄清，多子问题场景先不处理。
        if (CollUtil.isEmpty(subIntents) || subIntents.size() != 1) {
            return null;
        }

        // 先过滤掉低分候选，避免无意义的弱匹配进入歧义判断。
        List<NodeScore> candidates = filterCandidates(subIntents.get(0).nodeScores());
        if (candidates.size() < 2) {
            return null;
        }

        // 以标准化后的名称分组，将同名但来源不同的候选归到同一主题下。
        Map<String, List<NodeScore>> grouped = candidates.stream()
                .filter(ns -> StrUtil.isNotBlank(ns.getNode().getName()))
                .collect(Collectors.groupingBy(ns -> normalizeName(ns.getNode().getName())));

        // 选择最强的歧义分组：至少两个候选、分数接近、且落在不同系统下。
        Optional<Map.Entry<String, List<NodeScore>>> best = grouped.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), sortByScore(entry.getValue())))
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> passScoreRatio(entry.getValue()))
                .filter(entry -> hasMultipleSystems(entry.getValue()))
                .max(Comparator.comparingDouble(entry -> entry.getValue().get(0).getScore()));

        if (best.isEmpty()) {
            return null;
        }

        List<NodeScore> groupScores = best.get().getValue();
        String topicName = Optional.ofNullable(groupScores.get(0).getNode().getName())
                .orElse(best.get().getKey());
        List<String> optionIds = collectSystemOptions(groupScores);
        if (optionIds.size() < 2) {
            return null;
        }
        return new AmbiguityGroup(topicName, trimOptions(optionIds));
    }

    /**
     * 依据最小分数阈值筛出可参与歧义判断的候选意图。
     */
    private List<NodeScore> filterCandidates(List<NodeScore> scores) {
        if (CollUtil.isEmpty(scores)) {
            return List.of();
        }
        return NodeScoreFilters.kb(scores, RAGConstant.INTENT_MIN_SCORE);
    }

    /**
     * 将同一歧义分组中的候选节点映射为系统级选项，并保留首次出现顺序。
     */
    private List<String> collectSystemOptions(List<NodeScore> groupScores) {
        Set<String> ordered = new LinkedHashSet<>();
        for (NodeScore score : groupScores) {
            IntentNode node = score.getNode();
            String systemId = resolveSystemNodeId(node);
            if (StrUtil.isNotBlank(systemId)) {
                ordered.add(systemId);
            }
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 判断是否应跳过澄清。
     * 当用户问题中已经明确出现某个候选系统名时，认为用户表达已足够明确。
     */
    private boolean shouldSkipGuidance(String question, List<String> systemNames) {
        if (StrUtil.isBlank(question) || CollUtil.isEmpty(systemNames)) {
            return false;
        }
        String normalizedQuestion = normalizeName(question);
        for (String name : systemNames) {
            if (StrUtil.isBlank(name)) {
                continue;
            }
            for (String alias : buildSystemAliases(name)) {
                if (alias.length() < 2) {
                    continue;
                }
                if (normalizedQuestion.contains(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将候选系统 ID 解析为可展示的系统名称。
     */
    private List<String> resolveOptionNames(List<String> optionIds) {
        if (CollUtil.isEmpty(optionIds)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String id : optionIds) {
            IntentNode node = intentNodeRegistry.getNodeById(id);
            if (node == null) {
                continue;
            }
            String name = StrUtil.blankToDefault(node.getName(), node.getId());
            names.add(name);
        }
        return names;
    }

    /**
     * 构建系统名称的匹配别名。
     * 当前仅使用标准化后的名称，后续可扩展为简称、英文名等别名。
     */
    private List<String> buildSystemAliases(String systemName) {
        if (StrUtil.isBlank(systemName)) {
            return List.of();
        }
        String normalized = normalizeName(systemName);
        List<String> aliases = new ArrayList<>();
        if (StrUtil.isNotBlank(normalized)) {
            aliases.add(normalized);
        }
        return aliases;
    }

    /**
     * 判断同一分组内前两名候选的分差是否足够接近。
     * 分数越接近，越说明模型难以直接区分，需要人工澄清。
     */
    private boolean passScoreRatio(List<NodeScore> group) {
        if (group.size() < 2) {
            return false;
        }
        double top = group.get(0).getScore();
        double second = group.get(1).getScore();
        if (top <= 0) {
            return false;
        }
        double ratio = second / top;
        return ratio >= Optional.ofNullable(guidanceProperties.getAmbiguityScoreRatio()).orElse(0.0D);
    }

    /**
     * 判断同一主题下的候选是否来自多个系统。
     * 如果都落在同一个系统内，则不属于需要用户选择的系统级歧义。
     */
    private boolean hasMultipleSystems(List<NodeScore> group) {
        Set<String> systems = group.stream()
                .map(NodeScore::getNode)
                .map(this::resolveSystemNodeId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        return systems.size() > 1;
    }

    /**
     * 从任意意图节点向上回溯，定位它所属的系统级节点 ID。
     * 这里将 CATEGORY 且父级为 DOMAIN 或空的节点视为系统边界。
     */
    private String resolveSystemNodeId(IntentNode node) {
        if (node == null) {
            return "";
        }
        IntentNode current = node;
        IntentNode parent = fetchParent(current);
        for (; ; ) {
            IntentLevel level = current.getLevel();
            // 命中系统边界时直接返回当前节点。
            if (level == IntentLevel.CATEGORY && (parent == null || parent.getLevel() == IntentLevel.DOMAIN)) {
                return current.getId();
            }
            // 已到根节点但未命中预期层级时，退化返回当前节点，保证结果可用。
            if (parent == null) {
                return current.getId();
            }
            current = parent;
            parent = fetchParent(current);
        }
    }

    /**
     * 读取指定节点的父节点。
     */
    private IntentNode fetchParent(IntentNode node) {
        if (node == null || StrUtil.isBlank(node.getParentId())) {
            return null;
        }
        return intentNodeRegistry.getNodeById(node.getParentId());
    }

    /**
     * 按分数从高到低排序候选节点。
     */
    private List<NodeScore> sortByScore(List<NodeScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
    }

    /**
     * 根据配置限制最多返回多少个澄清选项。
     */
    private List<String> trimOptions(List<String> optionIds) {
        int maxOptions = Optional.ofNullable(guidanceProperties.getMaxOptions()).orElse(optionIds.size());
        if (optionIds.size() <= maxOptions) {
            return optionIds;
        }
        return optionIds.subList(0, maxOptions);
    }

    /**
     * 组装用于向用户追问的提示词内容。
     */
    private String buildPrompt(String topicName, List<String> optionIds) {
        String options = renderOptions(optionIds);
        return promptTemplateLoader.render(
                RAGConstant.GUIDANCE_PROMPT_PATH,
                Map.of(
                        "topic_name", StrUtil.blankToDefault(topicName, ""),
                        "options", options
                )
        );
    }

    /**
     * 将候选系统渲染为编号列表，供提示词模板直接插入。
     */
    private String renderOptions(List<String> optionIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < optionIds.size(); i++) {
            String id = optionIds.get(i);
            IntentNode node = intentNodeRegistry.getNodeById(id);
            String name = node == null || StrUtil.isBlank(node.getName()) ? id : node.getName();
            sb.append(i + 1).append(") ").append(name).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 对名称做统一标准化，去掉大小写、标点和空白差异，便于做模糊匹配与分组。
     */
    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        return cleaned.replaceAll("[\\p{Punct}\\s]+", "");
    }

    /**
     * 歧义分组信息，包含待澄清主题名及其对应的候选系统列表。
     */
    private record AmbiguityGroup(String topicName, List<String> optionIds) {
    }
}
