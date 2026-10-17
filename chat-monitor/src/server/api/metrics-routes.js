import { API_BASE_PATH } from '../../shared/api-contract.js';

export async function registerMetricsRoutes(application, config, runtime) {
  application.get(`${API_BASE_PATH}/environments/:environmentId/overview`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    return withBounds(reply, () => ({
      schemaVersion: 1,
      environmentId: profile.id,
      ...runtime.metricRepository.overview(profile.id, request.query.hours)
    }));
  });

  application.get(`${API_BASE_PATH}/environments/:environmentId/metrics`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    return withBounds(reply, () => ({
      schemaVersion: 1,
      environmentId: profile.id,
      ...runtime.metricRepository.timeSeries(profile.id, request.query)
    }));
  });

  application.get(`${API_BASE_PATH}/environments/:environmentId/operations`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    return withBounds(reply, () => ({
      schemaVersion: 1,
      environmentId: profile.id,
      ...runtime.metricRepository.operations(profile.id, request.query)
    }));
  });

  application.get(`${API_BASE_PATH}/environments/:environmentId/failures`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    return withBounds(reply, () => ({
      schemaVersion: 1,
      environmentId: profile.id,
      ...runtime.metricRepository.failures(profile.id, request.query)
    }));
  });

  application.get(`${API_BASE_PATH}/environments/:environmentId/alerts`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    return {
      schemaVersion: 1,
      environmentId: profile.id,
      alerts: runtime.alertService.list(profile.id)
    };
  });

  application.post(`${API_BASE_PATH}/environments/:environmentId/alerts/:ruleId/acknowledge`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    const acknowledged = runtime.alertService.acknowledge(profile.id, request.params.ruleId);
    return acknowledged ? reply.code(204).send() : reply.code(409).send({ code: 'ALERT_NOT_FIRING' });
  });

  application.post(`${API_BASE_PATH}/environments/:environmentId/alerts/:ruleId/suppress`, async (request, reply) => {
    const profile = findProfile(config, request.params.environmentId, reply);
    if (!profile) return;
    return withBounds(reply, () => {
      const suppressed = runtime.alertService.suppress(
        profile.id, request.params.ruleId, request.body?.hours
      );
      return suppressed ? reply.code(204).send() : reply.code(404).send({ code: 'ALERT_NOT_FOUND' });
    });
  });
}

function findProfile(config, environmentId, reply) {
  const profile = config.environments.find(({ id }) => id === environmentId);
  if (!profile) {
    reply.code(404).send({ code: 'ENVIRONMENT_NOT_FOUND' });
    return null;
  }
  return profile;
}

function withBounds(reply, operation) {
  try {
    return operation();
  } catch (error) {
    if (error instanceof RangeError) {
      return reply.code(400).send({ code: 'INVALID_QUERY_BOUNDS', message: error.message });
    }
    throw error;
  }
}
