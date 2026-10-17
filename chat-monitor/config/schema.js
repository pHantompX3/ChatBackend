import { z } from 'zod';
import { DEFAULTS } from './defaults.js';

const optionalTrimmed = z.preprocess(
  (value) => typeof value === 'string' && value.trim() === '' ? undefined : value,
  z.string().trim().min(1).optional()
);
const booleanFromEnvironment = (defaultValue) => z
  .enum(['true', 'false'])
  .default(String(defaultValue))
  .transform((value) => value === 'true');
const boundedInteger = (minimum, maximum) => z.coerce.number().int().min(minimum).max(maximum);

const environmentSchema = z.object({
  CHAT_MONITOR_DEPLOYMENT_MODE: z.enum(['native', 'container']).default(DEFAULTS.deploymentMode),
  CHAT_MONITOR_MODE: z.enum(['fixture', 'sql']).default(DEFAULTS.mode),
  CHAT_MONITOR_HOST: z.enum(['127.0.0.1', '::1', '0.0.0.0']).default(DEFAULTS.host),
  CHAT_MONITOR_PORT: boundedInteger(1024, 65535).default(DEFAULTS.port),
  CHAT_MONITOR_LOG_LEVEL: z.enum(['silent', 'error', 'warn', 'info', 'debug']).default(DEFAULTS.logLevel),
  CHAT_MONITOR_FIXTURE_PATH: z.string().trim().min(1).default(DEFAULTS.fixturePath),
  CHAT_MONITOR_SQLITE_PATH: z.string().trim().min(1).default(DEFAULTS.sqlitePath),
  CHAT_MONITOR_POLL_INTERVAL_SECONDS: boundedInteger(5, 3600).default(DEFAULTS.pollIntervalSeconds),
  CHAT_MONITOR_PAGE_SIZE: boundedInteger(1, 1000).default(DEFAULTS.pageSize),
  CHAT_MONITOR_DETAIL_RETENTION_DAYS: boundedInteger(62, 730).default(DEFAULTS.detailRetentionDays),
  CHAT_MONITOR_AGGREGATE_RETENTION_DAYS: boundedInteger(90, 3650).default(DEFAULTS.aggregateRetentionDays),
  CHAT_MONITOR_ENVIRONMENTS_FILE: optionalTrimmed,
  CHAT_MONITOR_DEFAULT_ENVIRONMENT_ID: optionalTrimmed,
  CHAT_MONITOR_SOURCE_HOST: optionalTrimmed,
  CHAT_MONITOR_SOURCE_PORT: boundedInteger(1, 65535).default(DEFAULTS.sourcePort),
  CHAT_MONITOR_SOURCE_DATABASE: z.string().trim().min(1).default(DEFAULTS.sourceDatabase),
  CHAT_MONITOR_SOURCE_USERNAME: optionalTrimmed,
  CHAT_MONITOR_SOURCE_PASSWORD: optionalTrimmed,
  CHAT_MONITOR_SOURCE_PASSWORD_FILE: optionalTrimmed,
  CHAT_MONITOR_SOURCE_ENCRYPT: booleanFromEnvironment(DEFAULTS.sourceEncrypt),
  CHAT_MONITOR_SOURCE_TRUST_SERVER_CERTIFICATE: booleanFromEnvironment(DEFAULTS.sourceTrustServerCertificate)
}).superRefine((value, context) => {
  if (value.CHAT_MONITOR_DEPLOYMENT_MODE === 'native' && value.CHAT_MONITOR_HOST === '0.0.0.0') {
    context.addIssue({
      code: 'custom',
      path: ['CHAT_MONITOR_HOST'],
      message: 'native deployment must bind to loopback'
    });
  }

  if (value.CHAT_MONITOR_DEPLOYMENT_MODE === 'container' && value.CHAT_MONITOR_HOST !== '0.0.0.0') {
    context.addIssue({
      code: 'custom',
      path: ['CHAT_MONITOR_HOST'],
      message: 'container deployment must bind internally to 0.0.0.0 and rely on loopback-only port publication'
    });
  }

  if (value.CHAT_MONITOR_ENVIRONMENTS_FILE || value.CHAT_MONITOR_MODE !== 'sql') {
    return;
  }

  for (const key of ['CHAT_MONITOR_SOURCE_HOST', 'CHAT_MONITOR_SOURCE_USERNAME']) {
    if (!value[key]) {
      context.addIssue({
        code: 'custom',
        path: [key],
        message: `${key} is required in sql mode`
      });
    }
  }

  if (!value.CHAT_MONITOR_SOURCE_PASSWORD && !value.CHAT_MONITOR_SOURCE_PASSWORD_FILE) {
    context.addIssue({
      code: 'custom',
      path: ['CHAT_MONITOR_SOURCE_PASSWORD_FILE'],
      message: 'CHAT_MONITOR_SOURCE_PASSWORD or CHAT_MONITOR_SOURCE_PASSWORD_FILE is required in sql mode'
    });
  }

  if (value.CHAT_MONITOR_SOURCE_PASSWORD && value.CHAT_MONITOR_SOURCE_PASSWORD_FILE) {
    context.addIssue({
      code: 'custom',
      path: ['CHAT_MONITOR_SOURCE_PASSWORD_FILE'],
      message: 'configure only one SQL source password input'
    });
  }

  if (!value.CHAT_MONITOR_SOURCE_ENCRYPT) {
    context.addIssue({
      code: 'custom',
      path: ['CHAT_MONITOR_SOURCE_ENCRYPT'],
      message: 'SQL transport encryption cannot be disabled'
    });
  }

  if (value.CHAT_MONITOR_SOURCE_TRUST_SERVER_CERTIFICATE) {
    context.addIssue({
      code: 'custom',
      path: ['CHAT_MONITOR_SOURCE_TRUST_SERVER_CERTIFICATE'],
      message: 'SQL certificate verification cannot be disabled'
    });
  }
});

export function parseEnvironment(environment = process.env) {
  const parsed = environmentSchema.parse(environment);

  return Object.freeze({
    deploymentMode: parsed.CHAT_MONITOR_DEPLOYMENT_MODE,
    mode: parsed.CHAT_MONITOR_MODE,
    host: parsed.CHAT_MONITOR_HOST,
    port: parsed.CHAT_MONITOR_PORT,
    logLevel: parsed.CHAT_MONITOR_LOG_LEVEL,
    fixturePath: parsed.CHAT_MONITOR_FIXTURE_PATH,
    sqlitePath: parsed.CHAT_MONITOR_SQLITE_PATH,
    pollIntervalSeconds: parsed.CHAT_MONITOR_POLL_INTERVAL_SECONDS,
    pageSize: parsed.CHAT_MONITOR_PAGE_SIZE,
    detailRetentionDays: parsed.CHAT_MONITOR_DETAIL_RETENTION_DAYS,
    aggregateRetentionDays: parsed.CHAT_MONITOR_AGGREGATE_RETENTION_DAYS,
    environmentsFile: parsed.CHAT_MONITOR_ENVIRONMENTS_FILE,
    defaultEnvironmentId: parsed.CHAT_MONITOR_DEFAULT_ENVIRONMENT_ID,
    source: Object.freeze({
      host: parsed.CHAT_MONITOR_SOURCE_HOST,
      port: parsed.CHAT_MONITOR_SOURCE_PORT,
      database: parsed.CHAT_MONITOR_SOURCE_DATABASE,
      username: parsed.CHAT_MONITOR_SOURCE_USERNAME,
      password: parsed.CHAT_MONITOR_SOURCE_PASSWORD,
      passwordFile: parsed.CHAT_MONITOR_SOURCE_PASSWORD_FILE,
      encrypt: parsed.CHAT_MONITOR_SOURCE_ENCRYPT,
      trustServerCertificate: parsed.CHAT_MONITOR_SOURCE_TRUST_SERVER_CERTIFICATE
    })
  });
}

const environmentId = z.string().trim().regex(/^[a-z][a-z0-9-]{0,31}$/);
const environmentClassification = z.enum(['local', 'development', 'test', 'staging', 'production']);
const fixtureProfile = z.object({
  id: environmentId,
  label: z.string().trim().min(1).max(60),
  classification: environmentClassification,
  mode: z.literal('fixture'),
  fixturePath: z.string().trim().min(1),
  pollIntervalSeconds: boundedInteger(5, 3600).optional(),
  pageSize: boundedInteger(1, 1000).optional()
}).strict();
const plannedProfile = z.object({
  id: environmentId,
  label: z.string().trim().min(1).max(60),
  classification: environmentClassification,
  mode: z.literal('planned'),
  unavailableReason: z.string().trim().min(1).max(240)
}).strict();
const sqlProfile = z.object({
  id: environmentId,
  label: z.string().trim().min(1).max(60),
  classification: environmentClassification,
  mode: z.literal('sql'),
  pollIntervalSeconds: boundedInteger(5, 3600).optional(),
  pageSize: boundedInteger(1, 1000).optional(),
  source: z.object({
    host: z.string().trim().min(1),
    port: boundedInteger(1, 65535).default(DEFAULTS.sourcePort),
    database: z.string().trim().min(1).default(DEFAULTS.sourceDatabase),
    username: z.string().trim().min(1),
    passwordFile: z.string().trim().min(1),
    encrypt: z.literal(true).default(true),
    trustServerCertificate: z.literal(false).default(false)
  }).strict()
}).strict();

const environmentsDocumentSchema = z.object({
  schemaVersion: z.literal(1),
  defaultEnvironmentId: environmentId,
  environments: z.array(
    z.discriminatedUnion('mode', [fixtureProfile, sqlProfile, plannedProfile])
  ).min(1).max(20)
}).strict().superRefine((document, context) => {
  const identifiers = new Set();
  for (const [index, profile] of document.environments.entries()) {
    if (identifiers.has(profile.id)) {
      context.addIssue({
        code: 'custom',
        path: ['environments', index, 'id'],
        message: `duplicate environment id: ${profile.id}`
      });
    }
    identifiers.add(profile.id);
  }
  if (!identifiers.has(document.defaultEnvironmentId)) {
    context.addIssue({
      code: 'custom',
      path: ['defaultEnvironmentId'],
      message: 'default environment must reference a configured environment'
    });
  }
});

export function parseEnvironmentsDocument(document) {
  return environmentsDocumentSchema.parse(document);
}
