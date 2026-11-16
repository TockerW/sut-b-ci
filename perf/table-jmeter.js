const fs = require('fs');
const rows = ['baseline', 'load', 'stress'].map((p) => {
  const t = JSON.parse(fs.readFileSync(`report-jmeter-${p}/statistics.json`)).Total;
  return {
    test: p,
    'avg, ms': t.meanResTime.toFixed(2),
    'p95, ms': t.pct2ResTime.toFixed(2),
    'max, ms': t.maxResTime.toFixed(0),
    'req/s': t.throughput.toFixed(2),
    'error %': t.errorPct.toFixed(2),
  };
});
console.table(rows);
