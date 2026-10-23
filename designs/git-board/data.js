// Mock numbers for the mockups: 30 days of commits per repo, today last. Weekends low.
const REPOS = [
  { name: "wevantage",      group: "we-project", d: [9,12,7,10,8,2,1,14,11,9,13,10,3,2,12,15,9,11,10,1,2,13,9,12,8,11,2,1,10,7],  last: "李想 · 12 分钟前",  msg: "feat(board): 团队行按预警数排序",  ci: "ok",  tier: "hot" },
  { name: "api-gateway",    group: "platform",   d: [5,6,4,7,5,1,0,6,8,5,6,4,0,1,7,6,5,8,4,0,0,6,5,7,6,5,1,0,8,5],       last: "王敏 · 38 分钟前",  msg: "fix: 限流窗口按租户隔离",            ci: "run", tier: "hot" },
  { name: "mobile-app",     group: "we-project", d: [3,4,6,5,7,2,0,4,6,7,5,6,1,0,5,7,8,6,4,0,1,6,5,4,7,6,0,0,5,4],       last: "陈宇 · 1 小时前",   msg: "ui: 看板行高度自适应",              ci: "ok",  tier: "hot" },
  { name: "collector",      group: "we-project", d: [2,3,1,4,2,0,0,3,2,4,3,2,0,0,2,3,4,2,3,0,0,4,3,2,3,2,0,0,3,2],       last: "林晓 · 2 小时前",   msg: "collector: 推送重试退避",           ci: "ok",  tier: "hot" },
  { name: "data-pipeline",  group: "platform",   d: [1,2,3,2,1,0,0,2,1,3,2,1,0,0,1,2,3,1,2,0,0,2,1,3,2,1,0,0,2,1],       last: "周杰 · 昨天 18:40", msg: "etl: 日表分区按周归档",             ci: "bad", tier: "warm" },
  { name: "admin-console",  group: "platform",   d: [2,1,0,2,1,0,0,1,2,0,1,2,0,0,1,0,2,1,0,0,0,1,2,1,0,1,0,0,1,0],       last: "赵琳 · 昨天 11:05", msg: "chore: 升级 next 15.3",             ci: "ok",  tier: "warm" },
  { name: "design-system",  group: "fe",         d: [0,1,0,0,1,0,0,0,1,0,0,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0],       last: "孙悦 · 3 天前",     msg: "tokens: 深色模式对比度",           ci: "ok",  tier: "cold" },
  { name: "cleanyourtv",    group: "pang",       d: [0,0,2,3,1,0,0,2,4,3,2,1,0,0,3,4,2,3,1,0,0,2,3,4,3,2,0,0,4,3],       last: "pang · 25 分钟前",  msg: "meeting-room: 雪山壁纸",            ci: "ok",  tier: "hot" },
];
const DAYS = 30;
function total(i) { return REPOS.reduce((s, r) => s + r.d[i], 0); }
function isWeekend(i) { return ((i + 5) % 7) >= 5; } // today (i=29) is Friday
function bars(el, arr, maxH, cls) {
  const max = Math.max(...arr) || 1;
  el.innerHTML = arr.map((v, i) => `<i class="${i === DAYS - 1 ? "today" : isWeekend(i) ? "we" : ""}" style="height:${Math.max(3, Math.round(v / max * maxH))}px"></i>`).join("");
}
function fmt(n) { return String(n); }
