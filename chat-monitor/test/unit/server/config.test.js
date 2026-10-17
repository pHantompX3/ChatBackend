import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { parseEnvironment } from '../../../config/schema.js';
import { loadConfig, publicConfig } from '../../../src/server/config.js';

test('fixture configuration is safe by default', () => {
  const config = loadConfig({}, process.cwd());

  assert.equal(config.mode, 'fixture');
  assert.equal(config.deploymentMode, 'native');
  assert.equal(config.host, '127.0.0.1');
  assert.equal(config.detailRetentionDays, 90);
  assert.equal(config.environments.length, 1);
  assert.equal(config.environments[0].id, 'fixture');
});

test('blank optional Compose values are treated as absent', () => {
  const config = parseEnvironment({
    CHAT_MONITOR_SOURCE_HOST: '',
    CHAT_MONITOR_SOURCE_USERNAME: '  '
  });
  assert.equal(config.source.host, undefined);
  assert.equal(config.source.username, undefined);
});

test('non-loopback binding is rejected', () => {
  assert.throws(
    () => parseEnvironment({ CHAT_MONITOR_HOST: '0.0.0.0' }),
    /native deployment must bind to loopback/
  );
});

test('container binding is explicit and compatible with loopback-only port publication', () => {
  const config = parseEnvironment({
    CHAT_MONITOR_DEPLOYMENT_MODE: 'container',
    CHAT_MONITOR_HOST: '0.0.0.0'
  });
  assert.equal(config.deploymentMode, 'container');
  assert.throws(
    () => parseEnvironment({
      CHAT_MONITOR_DEPLOYMENT_MODE: 'container',
      CHAT_MONITOR_HOST: '127.0.0.1'
    }),
    /container deployment must bind internally/
  );
});

test('sql mode requires credentials and verified TLS', () => {
  assert.throws(
    () => parseEnvironment({ CHAT_MONITOR_MODE: 'sql' }),
    /required in sql mode/
  );

  assert.throws(
    () => parseEnvironment({
      CHAT_MONITOR_MODE: 'sql',
      CHAT_MONITOR_SOURCE_HOST: 'sql.internal',
      CHAT_MONITOR_SOURCE_USERNAME: 'monitor',
      CHAT_MONITOR_SOURCE_PASSWORD: 'not-a-real-secret',
      CHAT_MONITOR_SOURCE_ENCRYPT: 'false'
    }),
    /transport encryption cannot be disabled/
  );

  assert.throws(
    () => parseEnvironment({
      CHAT_MONITOR_MODE: 'sql',
      CHAT_MONITOR_SOURCE_HOST: 'sql.internal',
      CHAT_MONITOR_SOURCE_USERNAME: 'monitor',
      CHAT_MONITOR_SOURCE_PASSWORD: 'not-a-real-secret',
      CHAT_MONITOR_SOURCE_TRUST_SERVER_CERTIFICATE: 'true'
    }),
    /cannot be disabled/
  );
});

test('sql mode can read its password from a server-only mounted file', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-monitor-config-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  fs.writeFileSync(path.join(directory, 'password'), 'file-secret\n', { mode: 0o600 });

  const config = loadConfig({
    CHAT_MONITOR_MODE: 'sql',
    CHAT_MONITOR_SOURCE_HOST: 'sql.internal',
    CHAT_MONITOR_SOURCE_USERNAME: 'monitor',
    CHAT_MONITOR_SOURCE_PASSWORD_FILE: './password'
  }, directory);

  assert.equal(config.environments[0].source.password, 'file-secret');
  assert.equal(JSON.stringify(publicConfig(config)).includes('file-secret'), false);
});

test('public configuration never returns source credentials or paths', () => {
  const config = loadConfig({
    CHAT_MONITOR_MODE: 'sql',
    CHAT_MONITOR_SOURCE_HOST: 'sql.internal',
    CHAT_MONITOR_SOURCE_USERNAME: 'monitor',
    CHAT_MONITOR_SOURCE_PASSWORD: 'not-a-real-secret'
  }, process.cwd());

  const safeConfig = publicConfig(config);
  assert.equal(JSON.stringify(safeConfig).includes('not-a-real-secret'), false);
  assert.equal(Object.hasOwn(safeConfig, 'source'), false);
  assert.equal(safeConfig.environments[0].sqlTlsVerification, true);
});

test('environment registry resolves profiles and secrets without exposing them', (context) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-monitor-environments-'));
  context.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  fs.writeFileSync(path.join(directory, 'fixture.json'), JSON.stringify({ schemaVersion: 1, events: [] }));
  fs.writeFileSync(path.join(directory, 'password'), 'profile-secret\n', { mode: 0o600 });
  fs.writeFileSync(path.join(directory, 'environments.json'), JSON.stringify({
    schemaVersion: 1,
    defaultEnvironmentId: 'local-dev',
    environments: [
      {
        id: 'local-dev', label: 'Local development', classification: 'local',
        mode: 'fixture', fixturePath: './fixture.json'
      },
      {
        id: 'production', label: 'Production', classification: 'production', mode: 'sql',
        source: {
          host: 'sql.internal', username: 'monitor', passwordFile: './password',
          encrypt: true, trustServerCertificate: false
        }
      },
      {
        id: 'future-production', label: 'Future production', classification: 'production',
        mode: 'planned', unavailableReason: 'Production has not been deployed.'
      }
    ]
  }));

  const config = loadConfig({ CHAT_MONITOR_ENVIRONMENTS_FILE: './environments.json' }, directory);
  assert.equal(config.environments.length, 3);
  assert.equal(config.environments[1].source.password, 'profile-secret');
  assert.equal(config.environments[1].source.passwordFile, path.join(directory, 'password'));
  assert.equal(config.environments[2].mode, 'planned');
  assert.equal(config.environments[2].unavailableReason, 'Production has not been deployed.');
  assert.equal(JSON.stringify(publicConfig(config)).includes('profile-secret'), false);
  assert.equal(JSON.stringify(publicConfig(config)).includes(directory), false);
});
