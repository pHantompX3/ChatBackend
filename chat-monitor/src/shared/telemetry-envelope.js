import { z } from 'zod';

const identifier = z.string().trim().min(1).max(160);
const timestamp = z.iso.datetime({ offset: true });
const dimensionValue = z.string().trim().min(1).max(160);
const measurementValue = z.number().finite().min(-1_000_000_000).max(1_000_000_000);

const envelopeSchema = z.object({
  eventId: identifier,
  schemaVersion: z.literal(1),
  sourceType: z.enum(['CHATBACKEND_AUDIT', 'CLIENT_TELEMETRY', 'HOST', 'SYNTHETIC']),
  sourceInstanceId: identifier,
  eventName: identifier.regex(/^[a-z0-9]+(?:[._-][a-z0-9]+)*$/),
  occurredAt: timestamp,
  receivedAt: timestamp,
  severity: z.enum(['INFO', 'WARNING', 'CRITICAL']),
  dimensions: z.record(identifier, dimensionValue).refine((value) => Object.keys(value).length <= 12, {
    message: 'dimensions must contain at most 12 entries'
  }),
  measurements: z.record(identifier, measurementValue).refine((value) => Object.keys(value).length <= 12, {
    message: 'measurements must contain at most 12 entries'
  })
}).strict();

const allowedDimensions = Object.freeze({
  'http.request.completed': new Set(['method', 'route', 'statusClass', 'responseCode', 'errorCode']),
  'client.recovery.completed': new Set(['clientType'])
});

const allowedMeasurements = Object.freeze({
  'http.request.completed': new Set(['durationMs', 'persistenceDelayMs']),
  'client.recovery.completed': new Set(['durationMs'])
});

export function parseTelemetryEnvelope(value) {
  const envelope = envelopeSchema.parse(value);
  const dimensionAllowlist = allowedDimensions[envelope.eventName];
  const measurementAllowlist = allowedMeasurements[envelope.eventName];

  if (!dimensionAllowlist || !measurementAllowlist) {
    throw new Error(`Unsupported telemetry event name: ${envelope.eventName}`);
  }

  rejectUnknownKeys(envelope.dimensions, dimensionAllowlist, 'dimension');
  rejectUnknownKeys(envelope.measurements, measurementAllowlist, 'measurement');
  return Object.freeze(envelope);
}

function rejectUnknownKeys(value, allowlist, kind) {
  for (const key of Object.keys(value)) {
    if (!allowlist.has(key)) {
      throw new Error(`Unsupported ${kind} for telemetry event: ${key}`);
    }
  }
}
