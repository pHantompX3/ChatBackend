import assert from 'node:assert/strict';
import test from 'node:test';
import { readAuditProjectionBounds, readAuditProjectionPage } from '../../../src/server/production/audit-projection.js';

test('SQL audit cursors preserve DATETIME2 precision and use textual UUID ordering', async () => {
  const inputs = [];
  let queryText = '';
  const pagePool = {
    request() {
      return {
        input(name, type, value) {
          inputs.push({ name, type, value });
          return this;
        },
        async query(text) {
          queryText = text;
          return {
            recordset: [{
              event_id: '00112233-4455-6677-8899-AABBCCDDEEFF',
              created_at_cursor: '2026-10-17T01:02:03.1234567',
              occurred_at: new Date('2026-10-17T01:02:03.100Z'),
              method: 'GET',
              route_template: '/api/v1/ping',
              response_status: 200,
              response_code: 'OK',
              duration_ms: 4,
              error_code: null
            }]
          };
        }
      };
    }
  };

  const records = await readAuditProjectionPage(pagePool, {
    createdAt: '2026-10-17T01:02:03.1234567Z',
    eventId: '00112233-4455-6677-8899-aabbccddeeff'
  }, 10, 'local');

  assert.equal(inputs.find(({ name }) => name === 'createdAt').value, '2026-10-17T01:02:03.1234567');
  assert.match(queryText, /LOWER\(CONVERT\(char\(36\), event_id\)\).*Latin1_General_100_BIN2/s);
  assert.equal(records[0].cursor.createdAt, '2026-10-17T01:02:03.1234567Z');
  assert.equal(records[0].cursor.eventId, '00112233-4455-6677-8899-aabbccddeeff');

  const bounds = await readAuditProjectionBounds({
    request: () => ({
      query: async () => ({
        recordsets: [
          [{ created_at_cursor: '2026-10-17T01:02:03.0000001', event_id: '00112233-4455-6677-8899-aabbccddeeff' }],
          [{ created_at_cursor: '2026-10-17T01:02:03.9999999', event_id: 'ffeeddcc-bbaa-9988-7766-554433221100' }]
        ]
      })
    })
  });
  assert.equal(bounds.retentionBoundary.createdAt, '2026-10-17T01:02:03.0000001Z');
  assert.equal(bounds.frontier.createdAt, '2026-10-17T01:02:03.9999999Z');
});
