/**
 * 文档模板工具
 * 提供常用笔记模板，支持 HTML 和 Markdown 两种格式
 */

export interface DocTemplate {
  key: string
  label: string
  description: string
  html: string
  markdown: string
}

export const DOC_TEMPLATES: DocTemplate[] = [
  {
    key: 'blank',
    label: '空白文档',
    description: '从零开始编写',
    html: '<p></p>',
    markdown: '',
  },
  {
    key: 'meeting',
    label: '会议纪要',
    description: '会议基本信息 + 议题 + 决议',
    html: `<h2>会议纪要</h2>
<p><strong>主题：</strong>请输入会议主题</p>
<p><strong>时间：</strong>YYYY-MM-DD HH:mm</p>
<p><strong>地点：</strong></p>
<p><strong>参会人员：</strong></p>
<h3>议题</h3>
<ol>
<li>议题一</li>
<li>议题二</li>
</ol>
<h3>讨论内容</h3>
<p></p>
<h3>决议</h3>
<ul>
<li>决议一</li>
<li>决议二</li>
</ul>
<h3>待办事项</h3>
<ul>
<li>[ ] 待办一 - 负责人 - 截止日期</li>
</ul>`,
    markdown: `## 会议纪要
**主题：** 请输入会议主题
**时间：** YYYY-MM-DD HH:mm
**地点：**
**参会人员：**

### 议题
1. 议题一
2. 议题二

### 讨论内容


### 决议
- 决议一
- 决议二

### 待办事项
- [ ] 待办一 - 负责人 - 截止日期
`,
  },
  {
    key: 'todo',
    label: '待办清单',
    description: '任务清单 + 优先级',
    html: `<h2>待办清单</h2>
<h3>本周</h3>
<ul>
<li>[ ] 高优先级任务</li>
<li>[ ] 中优先级任务</li>
<li>[ ] 低优先级任务</li>
</ul>
<h3>下周计划</h3>
<ul>
<li>[ ] 计划一</li>
</ul>`,
    markdown: `## 待办清单
### 本周
- [ ] 高优先级任务
- [ ] 中优先级任务
- [ ] 低优先级任务

### 下周计划
- [ ] 计划一
`,
  },
  {
    key: 'reading',
    label: '读书笔记',
    description: '书籍信息 + 摘录 + 心得',
    html: `<h2>读书笔记</h2>
<p><strong>书名：</strong></p>
<p><strong>作者：</strong></p>
<p><strong>阅读日期：</strong></p>
<h3>核心观点</h3>
<ol>
<li>观点一</li>
<li>观点二</li>
</ol>
<h3>精彩摘录</h3>
<blockquote>摘录内容</blockquote>
<h3>个人心得</h3>
<p></p>`,
    markdown: `## 读书笔记
**书名：**
**作者：**
**阅读日期：**

### 核心观点
1. 观点一
2. 观点二

### 精彩摘录
> 摘录内容

### 个人心得

`,
  },
  {
    key: 'api',
    label: '接口文档',
    description: 'API 接口规范模板',
    html: `<h2>接口文档</h2>
<h3>接口名称</h3>
<p><strong>请求方法：</strong>GET / POST</p>
<p><strong>路径：</strong>/api/xxx</p>
<p><strong>描述：</strong></p>
<h4>请求参数</h4>
<table border="1">
<thead><tr><th>参数</th><th>类型</th><th>必填</th><th>说明</th></tr></thead>
<tbody>
<tr><td>id</td><td>Long</td><td>是</td><td>资源ID</td></tr>
</tbody>
</table>
<h4>响应示例</h4>
<pre><code>{
  "code": 200,
  "data": {}
}</code></pre>`,
    markdown: `## 接口文档
### 接口名称
**请求方法：** GET / POST
**路径：** /api/xxx
**描述：**

#### 请求参数
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id   | Long | 是   | 资源ID |

#### 响应示例
\`\`\`json
{
  "code": 200,
  "data": {}
}
\`\`\``,
  },
  {
    key: 'weekly',
    label: '周报',
    description: '本周工作 + 下周计划',
    html: `<h2>周报</h2>
<p><strong>周期：</strong>YYYY-MM-DD ~ YYYY-MM-DD</p>
<h3>本周工作</h3>
<ol>
<li>工作项一 - 进度 100%</li>
<li>工作项二 - 进度 50%</li>
</ol>
<h3>遇到的问题</h3>
<ul>
<li>问题一</li>
</ul>
<h3>下周计划</h3>
<ol>
<li>计划一</li>
</ol>`,
    markdown: `## 周报
**周期：** YYYY-MM-DD ~ YYYY-MM-DD

### 本周工作
1. 工作项一 - 进度 100%
2. 工作项二 - 进度 50%

### 遇到的问题
- 问题一

### 下周计划
1. 计划一
`,
  },
]

/**
 * 根据格式和模板key获取初始内容
 */
export function getTemplateContent(format: 'html' | 'markdown', key: string): string {
  const tpl = DOC_TEMPLATES.find((t) => t.key === key)
  if (!tpl) return format === 'markdown' ? '' : '<p></p>'
  return format === 'markdown' ? tpl.markdown : tpl.html
}
