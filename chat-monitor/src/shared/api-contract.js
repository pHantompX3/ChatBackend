export const API_VERSION = 'v1';
export const API_BASE_PATH = `/api/${API_VERSION}`;

export function createEnvironmentsResponse({ defaultEnvironmentId, environments }) {
  return {
    schemaVersion: 1,
    defaultEnvironmentId,
    environments: environments.map(({ profile, state }) => ({
      id: profile.id,
      label: profile.label,
      classification: profile.classification,
      sourceMode: profile.mode,
      state
    }))
  };
}

export function createStatusResponse({ profile, collectorState, retainedRange, eventCount, now = new Date() }) {
  return {
    schemaVersion: 2,
    environment: {
      id: profile.id,
      label: profile.label,
      classification: profile.classification
    },
    state: collectorState?.state ?? 'CATCHING_UP',
    sourceMode: profile.mode,
    generatedAt: now.toISOString(),
    freshness: {
      lastSuccessfulCollectionAt: collectorState?.lastSuccessfulCollectionAt ?? null,
      lagSeconds: collectorState?.lagSeconds ?? null
    },
    retention: {
      oldestEventAt: retainedRange?.oldest ?? null,
      newestEventAt: retainedRange?.newest ?? null,
      eventCount: eventCount ?? 0
    },
    errorCode: collectorState?.lastErrorCode ?? null,
    message: collectorState?.detail ?? 'The collector has not completed its first cycle.'
  };
}
