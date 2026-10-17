import sql from 'mssql';
import { readAuditProjectionBounds, readAuditProjectionPage } from './audit-projection.js';
import { SourceUnavailableError } from '../collector/source-error.js';

export function createSqlServerSource(config) {
  const sourceInstanceId = `sql-${config.host}-${config.database}`.replace(/[^a-zA-Z0-9._-]/g, '_');
  let pool = createPool();
  let connected = false;

  return Object.freeze({
    sourceId: sourceInstanceId,
    async readBounds() {
      try {
        await connect();
        return await readAuditProjectionBounds(pool);
      } catch (error) {
        throw await unavailable(error);
      }
    },
    async readPage(after, limit) {
      try {
        await connect();
        return await readAuditProjectionPage(pool, after, limit, sourceInstanceId);
      } catch (error) {
        throw await unavailable(error);
      }
    },
    async close() {
      connected = false;
      await pool.close();
    }
  });

  async function connect() {
    if (!connected) {
      await pool.connect();
      connected = true;
    }
  }

  async function unavailable(error) {
    connected = false;
    await pool.close().catch(() => {});
    pool = createPool();
    return new SourceUnavailableError('The SQL monitoring source is unavailable', { cause: error });
  }

  function createPool() {
    return new sql.ConnectionPool({
      server: config.host,
      port: config.port,
      database: config.database,
      user: config.username,
      password: config.password,
      connectionTimeout: 5000,
      requestTimeout: 5000,
      pool: { min: 0, max: 2, idleTimeoutMillis: 30000 },
      options: {
        encrypt: config.encrypt,
        trustServerCertificate: config.trustServerCertificate,
        enableArithAbort: true
      }
    });
  }
}
