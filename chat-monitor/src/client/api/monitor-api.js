const REQUEST_TIMEOUT_MS = 5_000;

async function getJson(path) {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS)
  });

  if (!response.ok) {
    throw new Error(`Monitor request failed with status ${response.status}`);
  }

  return response.json();
}

export function getEnvironments() {
  return getJson('/api/v1/environments');
}

export function getEnvironmentStatus(environmentId) {
  return getJson(`/api/v1/environments/${encodeURIComponent(environmentId)}/status`);
}

export function getEnvironmentDashboard(environmentId, hours) {
  const base = `/api/v1/environments/${encodeURIComponent(environmentId)}`;
  const query = `?hours=${encodeURIComponent(hours)}`;
  return Promise.all([
    getJson(`${base}/overview${query}`),
    getJson(`${base}/metrics${query}&resolution=hour`),
    getJson(`${base}/operations${query}&limit=10`),
    getJson(`${base}/failures${query}&limit=25`),
    getJson(`${base}/alerts`)
  ]).then(([overview, metrics, operations, failures, alerts]) => ({
    overview, metrics, operations, failures, alerts
  }));
}
