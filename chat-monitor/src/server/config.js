import path from 'node:path';
import fs from 'node:fs';
import { parseEnvironment, parseEnvironmentsDocument } from '../../config/schema.js';

export function loadConfig(environment = process.env, workingDirectory = process.cwd()) {
  const config = parseEnvironment(environment);
  const environmentsFile = config.environmentsFile
    ? path.resolve(workingDirectory, config.environmentsFile)
    : undefined;
  const environmentsDocument = environmentsFile ? parseEnvironmentsFile(environmentsFile) : undefined;
  const profiles = environmentsDocument
    ? loadEnvironmentProfiles(environmentsDocument, environmentsFile, config)
    : [loadLegacyProfile(config, workingDirectory)];
  const defaultEnvironmentId = environmentsDocument
    ? environmentsDocument.defaultEnvironmentId
    : config.defaultEnvironmentId ?? profiles[0].id;

  if (!profiles.some(({ id }) => id === defaultEnvironmentId)) {
    throw new Error('Configured default environment is not present in the environment registry');
  }

  return Object.freeze({
    ...config,
    fixturePath: path.resolve(workingDirectory, config.fixturePath),
    sqlitePath: path.resolve(workingDirectory, config.sqlitePath),
    environmentsFile,
    defaultEnvironmentId,
    environments: Object.freeze(profiles)
  });
}

export function publicConfig(config) {
  return Object.freeze({
    deploymentMode: config.deploymentMode,
    host: config.host,
    port: config.port,
    pollIntervalSeconds: config.pollIntervalSeconds,
    pageSize: config.pageSize,
    detailRetentionDays: config.detailRetentionDays,
    aggregateRetentionDays: config.aggregateRetentionDays,
    defaultEnvironmentId: config.defaultEnvironmentId,
    environments: config.environments.map(publicEnvironment)
  });
}

function loadEnvironmentProfiles(document, filePath, config) {
  const directory = path.dirname(filePath);
  return document.environments.map((profile) => loadProfile(profile, directory, config));
}

function parseEnvironmentsFile(filePath) {
  return parseEnvironmentsDocument(JSON.parse(fs.readFileSync(filePath, 'utf8')));
}

function loadLegacyProfile(config, workingDirectory) {
  const id = config.defaultEnvironmentId ?? (config.mode === 'fixture' ? 'fixture' : 'default');
  const profile = config.mode === 'fixture'
    ? {
        id,
        label: config.mode === 'fixture' ? 'Fixture' : 'Default',
        classification: 'local',
        mode: 'fixture',
        fixturePath: config.fixturePath
      }
    : {
        id,
        label: 'Default',
        classification: 'development',
        mode: 'sql',
        source: config.source
      };
  return loadProfile(profile, workingDirectory, config, true);
}

function loadProfile(profile, directory, config, allowInlinePassword = false) {
  const common = {
    id: profile.id,
    label: profile.label,
    classification: profile.classification,
    mode: profile.mode,
    pollIntervalSeconds: profile.pollIntervalSeconds ?? config.pollIntervalSeconds,
    pageSize: profile.pageSize ?? config.pageSize
  };
  if (profile.mode === 'planned') {
    return Object.freeze({
      ...common,
      unavailableReason: profile.unavailableReason
    });
  }
  if (profile.mode === 'fixture') {
    return Object.freeze({
      ...common,
      fixturePath: path.resolve(directory, profile.fixturePath)
    });
  }

  const passwordFile = profile.source.passwordFile
    ? path.resolve(directory, profile.source.passwordFile)
    : undefined;
  const password = passwordFile
    ? readRequiredSecret(passwordFile)
    : allowInlinePassword ? profile.source.password : undefined;
  if (!password) {
    throw new Error(`Environment ${profile.id} does not provide a readable SQL password file`);
  }
  return Object.freeze({
    ...common,
    source: Object.freeze({ ...profile.source, password, passwordFile })
  });
}

function publicEnvironment(profile) {
  return Object.freeze({
    id: profile.id,
    label: profile.label,
    classification: profile.classification,
    mode: profile.mode,
    pollIntervalSeconds: profile.pollIntervalSeconds,
    pageSize: profile.pageSize,
    sqlTlsVerification: profile.mode === 'sql'
      ? profile.source.encrypt && !profile.source.trustServerCertificate
      : null
  });
}

function readRequiredSecret(filePath) {
  const value = fs.readFileSync(filePath, 'utf8').trim();
  if (!value) {
    throw new Error('Configured SQL source password file is empty');
  }
  return value;
}
