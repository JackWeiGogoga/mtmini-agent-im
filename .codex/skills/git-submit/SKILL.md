---
name: git-submit
description: 当用户说“提交 git”“git 提交”“提交本次修改”“commit and push”“add commit push”时，安全检查本次工作区改动，执行 git add、git commit，并 push 当前分支；当用户明确要求“打 tag / create tag / release tag”时，可创建并推送 git tag；不创建 PR。
---

# Git Submit

用于轻量提交当前分支：检查范围、阻断敏感文件、提交、push；用户明确要求时可创建并推送 tag。这个 skill 不创建 PR；需要开 PR 时改用 `github:yeet`。

## 触发边界

只有用户当前消息明确要求提交或推送时，才执行本 skill，例如：

- `提交 git`
- `git 提交`
- `提交并推送`
- `commit and push`
- `add commit push`
- `用 git-submit 提交`

也可在用户明确要求 tag 时执行本 skill，例如：

- `提交并打 tag`
- `提交并创建 release tag`
- `打 tag release-20260524-1800`
- `create tag v1.2.3`
- `给当前提交打 tag 并推送`

不要因为完成了“实现”“修复”“优化”“改一下”等任务就自动提交。普通开发任务完成后，停在未提交状态，汇报变更和测试结果即可。

如果用户要求提交的是业务代码，提交前必须先展示：

```text
提交范围：
测试结果：
拟使用 commit message：
拟使用 tag：（仅当用户要求打 tag 时展示）
```

业务代码包括 `backend/`、`frontend/`、`h5/`、数据库迁移、生产配置模板和部署脚本。若测试未跑、测试失败、或工作区存在混杂改动，先停下来说明风险并等待用户确认；不要直接提交。

## 默认流程

1. 读取当前状态和差异摘要：

   ```bash
   git status -sb
   git status --short --untracked-files=all
   git diff --stat
   git diff --cached --stat
   git branch --show-current
   git remote -v
   git tag --list
   ```

2. 判断提交范围：

   - 如果本次对话里的工作只涉及当前所有改动，可以直接继续。
   - 如果工作区有明显无关改动，先告诉用户哪些文件混杂，等待用户指定要提交的文件。
   - 不要静默提交未理解的用户改动。
   - 纯 skill、文档、`.gitignore` 等低风险改动，在用户明确触发提交时可直接继续。
   - 业务代码改动即使范围清晰，也要先展示范围、测试结果和拟使用 message，再等待用户确认。

3. 敏感文件阻断：

   如果待提交文件命中以下模式，停止并提示用户确认处理方式，不要提交：

   ```text
   .env
   .env.*
   *secret*
   *secrets*
   *credential*
   *credentials*
   *.pem
   *.key
   *.p12
   *.pfx
   deploy-local/backend.env
   .codex/**/.env
   .codex/**/secrets.*
   .codex/**/credentials.*
   ```

   对新增的运维脚本、skill、配置说明，额外用 `rg` 搜一遍明显密钥片段：

   ```bash
   rg -n "PASSWORD=|SECRET=|TOKEN=|PRIVATE_KEY=|API_KEY=|sk-|AKID|BEGIN .*PRIVATE KEY" <paths>
   ```

   命中变量名不一定是泄密；命中真实值、证书正文或 token 时必须阻断。

4. 选择 add 方式：

   - 工作区范围清晰时，可用 `git add -A`。
   - 工作区混杂时，只 `git add <明确文件路径...>`。
   - 不使用 `git add -f`，除非用户明确要求提交被 ignore 的文件。

5. 提交：

   - 默认使用 Conventional Commits：

     ```text
     <type>(<scope>): <subject>
     ```

   - 常用 `type`：

     ```text
     feat      新增用户或开发者可用能力
     fix       修复问题
     docs      仅文档
     chore     工具链、配置、维护
     refactor  重构，不改变行为
     test      测试
     ci        CI 配置
     perf      性能优化
     ```

   - `scope` 用模块名或目录名，例如 `backend`、`frontend`、`h5`、`skills`、`ops`、`gitignore`、`deploy`。
   - `subject` 要简洁，不加句号。优先英文小写；如果项目上下文更适合中文，也可以用简洁中文。

     例：

     ```text
     feat(skills): add project codex workflows
     fix(ops): authenticate redis checks
     chore(gitignore): track project codex skills
     docs(ops): document redis persistence checks
     ```

   - 如果用户给了 message，优先使用用户的。
   - 提交前看一次 `git diff --cached --stat`。
   - 执行 `git commit -m "<message>"`。

6. Push：

   - 当前分支有 upstream：`git push`
   - 当前分支没有 upstream：`git push -u origin "$(git branch --show-current)"`
   - 如果当前在 `main` / `master` / 默认分支，先明确提醒；除非用户明确就是要推当前分支，否则建议改用分支 + PR。

7. Tag（仅当用户明确要求打 tag 时执行）：

   - 默认创建 annotated tag，不创建 lightweight tag：

     ```bash
     git tag -a "<tag-name>" -m "<tag-message>"
     git push origin "<tag-name>"
     ```

   - 如果用户给了 tag 名，优先使用用户给的名称。
   - 如果用户要求“稳定版 / release tag”但没给 tag 名：
     - 可按当前时间生成 `release-YYYYMMDD-HHMM`。
     - 如果是业务代码提交，必须在提交前确认块里展示拟使用 tag，等待用户确认。
     - 如果是纯 skill、文档等低风险改动，也要在执行前明确展示拟使用 tag。
   - tag message 默认与 tag 名一致；如果本轮刚提交了 commit，也可使用 commit message 作为 tag message。
   - tag 必须指向本轮成功提交并 push 后的 HEAD；如果用户只是要求“给当前提交打 tag”，则指向当前 HEAD。
   - 创建前必须检查 tag 是否已存在：

     ```bash
     git fetch --tags
     git rev-parse -q --verify "refs/tags/<tag-name>"
     ```

     如果本地或远端已存在同名 tag，停止并提示用户换名；不要覆盖、删除或移动已有 tag。
   - 只有打 tag、没有提交时：
     - 先确认工作区状态。
     - 如果工作区有未提交改动，先停下来说明 tag 会指向当前 HEAD 而不是未提交内容，等待用户确认。
     - 如果当前 HEAD 没有推到远端 upstream，先提示风险；除非用户明确确认，否则不要只推 tag。
   - 推送 tag 只推指定 tag：`git push origin "<tag-name>"`，不要用 `git push --tags`，避免把本地其他 tag 一起推上去。

8. 最终回复包含：

   ```text
   提交范围：
   Commit：
   Push：
   Tag：（仅当创建或推送 tag 时包含）
   风险/备注：
   ```

## 禁止行为

- 不运行 `git reset --hard`
- 不运行 `git checkout --`
- 不删除用户文件
- 不自动 amend
- 不自动 rebase
- 不自动 force push
- 不自动创建 tag；只有用户明确要求时才创建
- 不创建 lightweight tag，除非用户明确要求
- 不覆盖、删除、移动或强推已有 tag
- 不使用 `git push --tags`
- 不为了修正 commit message 自动改写已经 push 的提交历史；只有用户明确要求 amend/改提交信息时，才可说明风险后使用 `git commit --amend` 和 `git push --force-with-lease`
- 不把“实现完成”理解为“自动提交”
- 不创建 PR
- 不提交敏感文件

如果 `git commit` 失败是因为没有 staged changes，直接说明没有可提交内容。
