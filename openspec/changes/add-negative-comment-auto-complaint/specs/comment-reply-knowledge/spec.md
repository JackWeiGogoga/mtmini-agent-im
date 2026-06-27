## MODIFIED Requirements

### Requirement: 评论类型按有效星级判定
系统 SHALL 使用当前评价的有效星级作为评论好中差分类依据，优先取 `mt_comment.product_score` 与 `mt_comment.packing_score` 中的最低有效分；最低有效分为 0、1 或 2 时判定为差评 `BAD`，为 3 时判定为中评 `NEUTRAL`，为 4 或 5 时判定为好评 `GOOD`。系统 MUST NOT 将已废弃或固定返回 0 的 `food_comment_score` 作为优先判定依据。

#### Scenario: 差评分类
- **WHEN** 待回复评价的 `product_score` 与 `packing_score` 中最低有效分为 0、1 或 2
- **THEN** 系统将该评价的回复评论类型判定为 `BAD`

#### Scenario: 中评分类
- **WHEN** 待回复评价的 `product_score` 与 `packing_score` 中最低有效分为 3
- **THEN** 系统将该评价的回复评论类型判定为 `NEUTRAL`

#### Scenario: 好评分类
- **WHEN** 待回复评价的 `product_score` 与 `packing_score` 中最低有效分为 4 或 5
- **THEN** 系统将该评价的回复评论类型判定为 `GOOD`

#### Scenario: 单个有效评分
- **WHEN** 待回复评价的 `product_score` 与 `packing_score` 中只有一个字段是 0-5 范围内的有效分
- **THEN** 系统 SHALL 使用该有效分判定评论类型

#### Scenario: 评分缺失
- **WHEN** 待回复评价的 `product_score` 与 `packing_score` 均为空或均不在 0-5 范围内
- **THEN** 系统 MUST 记录异常日志，并 SHALL 暂不进入回复生成、美团回复调用或差评自动投诉链路

#### Scenario: 废弃评分字段固定为零
- **WHEN** 待回复评价的 `food_comment_score` 为 0，但 `product_score` 或 `packing_score` 存在有效非差评分
- **THEN** 系统 MUST NOT 因 `food_comment_score=0` 将该评价判定为差评
