# git常用命令

```
git add. 
git commit -m "注释"
git push
git pull

# 撤回提交，同时撤回git上的提交记录
git log --oneline --graph
git reset --hard d4e5f6c
git push -f origin dev-ngcard-20211207

# 撤回提交，保留历史提交记录
git checkout 你的分支名  # 例：git checkout main
git pull origin 你的分支名  # 例：git pull origin main

# 格式：git checkout <version标识> -- .
git checkout v2.1.3 -- .  # 用 Tag 标识（版本号绑定的 Tag），这个暂时还有问题，没找到原因
# 或
git checkout a8f7d2e -- .  # 用提交哈希标识（更精准，避免 Tag 重名）

git add .  # 暂存所有恢复的文件
git commit -m "feat: 回退到 Tag 版本 v1.0.0（保留历史提交）"
git push origin 你的分支名  # 例：git push origin main


```
