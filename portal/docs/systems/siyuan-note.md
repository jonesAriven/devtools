# 思源笔记

> 自托管的 SiYuan 笔记系统，用于个人笔记与知识块管理（Go + Electron + Vue 技术栈），数据私有、可离线优先。

## 基本信息

| 项 | 值 |
|----|----|
| 分类 | 工具软件 / 知识管理（笔记） |
| 版本 | SiYuan（自托管）；具体版本号未实采 (待确认) |
| 部署位置 | 应运行于 mykng（材料包：note.marschat.online → mykng:6806）；**实采（2026-09-05）mykng 上 6806 无监听、docker ps/docker ps -a 均无 siyuan/note 容器，当前疑似未运行/已停止** (待确认运行态) |
| 源码位置 | 开源 `siyuan-note/siyuan`（Go + Electron + Vue）；自托管部署，本地无构建仓库 |
| CI/CD | 无（自部署） |

## 访问入口

- 公网：`https://note.marschat.online`（腾讯云2号 nginx 终止 TLS 443）
- 内网：`http://192.168.31.105:6806`（mykng，当前不可达——服务未监听）
- Tailscale：`http://100.93.36.113:6806`（同未监听）

## 全链路

```
浏览器 → 腾讯云2号 nginx (:443, 域名 note.marschat.online)
       → http://100.93.36.113:6806  (mykng SiYuan, 端口 6806)
```

> 链路配置依据：材料包第 5 节 `note.marschat.online → http://100.93.36.113:6806`。
> **实采告警**：2026-09-05 在 mykng 上 `ss -tlnp | grep 6806` 无结果，`docker ps -a` 无 siyuan/note 相关容器——公网域名虽已配置反代，但后端当前未运行，访问会 502/超时。重启后需确认容器名/服务与 6806 端口映射是否仍匹配。

## 核心功能与使用

- 个人知识库/笔记：块级（block）编辑模型，支持双向链接、关系图、SQL 式查询、 Markdown/公众号导出等。
- 自托管优势：数据落在本机（mykng），不依赖商业云服务，适合存放私密/长期笔记。
- 典型场景：日常笔记、项目知识沉淀、资料归档；通过 `note.marschat.online` 随处访问。

> UI 具体操作未经实测（服务当前未运行），按 SiYuan 通用能力描述；如启用后需补充本地功能细节。

## 依赖与关联

- 依赖：mykng 宿主/容器运行时；SiYuan 工作空间数据目录（路径未实采）(待确认)；可选对象存储/图床（未实采）。
- 被依赖/关联系统：经腾讯云2号 nginx `note.marschat.online` 对外；与「记忆提炼面板」(memory.marschat.online) 同属知识类，但二者独立（SiYuan 为人工笔记，memory-panel 为自动提炼）。

## 运维要点

- 启停方式：**当前运行态待确认**。SiYuan 自托管常见为 Docker（`siyuan` 镜像，映射 6806）。建议排查：
  - `docker ps -a | grep -i siyuan` 确认容器是否存在/退出。
  - 若容器已删，需按原部署（镜像/卷）重建并映射 6806。
  - 具体 compose/启停文件未实采 (待确认)。
- 日志查看：`docker logs <siyuan容器>`；或 SiYuan 工作空间日志（未实采）。
- 数据与备份
  - 工作空间数据（笔记库）路径未实采 (待确认)，建议确认是否纳入 mykng 数据备份体系——笔记属高价值私密数据，丢失不可逆。
- 常见问题
  - 公网 502/打不开：多为 mykng 侧 SiYuan 未运行（与本次实采一致）。先确认 6806 是否监听再查 nginx。
  - 首次启动需设置访问口令（SiYuan 自带鉴权），口令应入 Vaultwarden，勿明文。

## 变更记录

- 2026-09-05 首次生成（portal 文档补全任务，AI 基于材料包链路 + mykng SSH 实采「6806 未监听/无容器」生成；显著标注当前服务疑似未运行，需确认）
