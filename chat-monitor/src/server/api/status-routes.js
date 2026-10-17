import {
  API_BASE_PATH,
  createEnvironmentsResponse,
  createStatusResponse
} from '../../shared/api-contract.js';
import { MONITOR_STATES } from '../../shared/monitor-states.js';

export async function registerStatusRoutes(application, config, runtime) {
  application.get(`${API_BASE_PATH}/environments`, async () => createEnvironmentsResponse({
    defaultEnvironmentId: config.defaultEnvironmentId,
    environments: config.environments.map((profile) => ({
      profile,
      state: collectorStateFor(profile)?.state ?? 'CATCHING_UP'
    }))
  }));

  application.get(`${API_BASE_PATH}/environments/:environmentId/status`, async (request, reply) => {
    return statusFor(request.params.environmentId, reply);
  });

  application.get(`${API_BASE_PATH}/status`, async (_request, reply) => {
    return statusFor(config.defaultEnvironmentId, reply);
  });

  function statusFor(environmentId, reply) {
    const profile = config.environments.find(({ id }) => id === environmentId);
    if (!profile) {
      return reply.code(404).send({ code: 'ENVIRONMENT_NOT_FOUND' });
    }
    const collectorState = collectorStateFor(profile);
    const collectsData = profile.mode !== 'planned';
    return createStatusResponse({
      profile,
      collectorState,
      retainedRange: collectsData ? runtime?.eventRepository.retainedRange(environmentId) : undefined,
      eventCount: collectsData ? runtime?.eventRepository.count(environmentId) : 0
    });
  }

  function collectorStateFor(profile) {
    if (profile.mode === 'planned') {
      return {
        state: MONITOR_STATES.SOURCE_UNAVAILABLE,
        lastSuccessfulCollectionAt: null,
        lastErrorCode: 'ENVIRONMENT_NOT_DEPLOYED',
        lagSeconds: null,
        detail: profile.unavailableReason
      };
    }
    return runtime?.stateRepository.get(profile.id);
  }
}
