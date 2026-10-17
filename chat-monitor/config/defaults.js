export const DEFAULTS = Object.freeze({
  deploymentMode: 'native',
  mode: 'fixture',
  host: '127.0.0.1',
  port: 8090,
  logLevel: 'info',
  fixturePath: './test/fixtures/normalized-events.json',
  sqlitePath: './var/data/chat-monitor.sqlite',
  pollIntervalSeconds: 60,
  pageSize: 500,
  detailRetentionDays: 90,
  aggregateRetentionDays: 365,
  defaultEnvironmentId: 'fixture',
  sourcePort: 1433,
  sourceDatabase: 'wl_chat',
  sourceEncrypt: true,
  sourceTrustServerCertificate: false
});
