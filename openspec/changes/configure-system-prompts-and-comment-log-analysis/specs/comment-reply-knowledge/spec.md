## MODIFIED Requirements

### Requirement: 评论类型按 food_comment_score 判定

系统 SHALL 使用 `mt_comment.product_score` 和 `mt_comment.packing_score` 中的最低有效分作为评论好中差分类依据，0-2 判定为差评 `BAD`，3 判定为中评 `NEUTRAL`，4-5 判定为好评 `GOOD`。系统 MUST NOT 使用已废弃且固定返回 0 的 `food_comment_score` 作为分类依据。

#### Scenario: 差评分类

- **WHEN** 待回复评价的 `product_score` 和 `packing_score` 中最低有效分为 0、1 或 2
- **THEN** 系统将该评价的回复评论类型判定为 `BAD`

#### Scenario: 中评分类

- **WHEN** 待回复评价的 `product_score` 和 `packing_score` 中最低有效分为 3
- **THEN** 系统将该评价的回复评论类型判定为 `NEUTRAL`

#### Scenario: 好评分类

- **WHEN** 待回复评价的 `product_score` 和 `packing_score` 中最低有效分为 4 或 5
- **THEN** 系统将该评价的回复评论类型判定为 `GOOD`

#### Scenario: 单个评分缺失

- **WHEN** 待回复评价的 `product_score` 或 `packing_score` 其中一个为空或不在 0-5 范围内，另一个为有效分
- **THEN** 系统 SHALL 使用有效分进行评论类型判定

#### Scenario: 评分均缺失

- **WHEN** 待回复评价的 `product_score` 和 `packing_score` 均为空或不在 0-5 范围内
- **THEN** 系统 MUST 记录异常日志，并 SHALL 暂不进入回复生成或美团回复调用链路
