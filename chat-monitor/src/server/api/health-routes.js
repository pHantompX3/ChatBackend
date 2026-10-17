import { API_BASE_PATH } from '../../shared/api-contract.js';

export async function registerHealthRoutes(application) {
  application.get(`${API_BASE_PATH}/health/live`, async () => ({ status: 'UP' }));
  application.get(`${API_BASE_PATH}/health/ready`, async () => ({ status: 'UP' }));
}
