import { getEnvironments, getEnvironmentDashboard, getEnvironmentStatus } from './api/monitor-api.js';
import { renderFreshnessBanner } from './components/freshness-banner.js';
import { renderRequestChart } from './components/chart-adapter.js';
import './styles/tokens.css';
import './styles/base.css';
import './styles/layout.css';
import './styles/components.css';

const REFRESH_INTERVAL_MS = 30_000;
const selectionStorageKey = 'chat-monitor.selected-environment';

const elements = {
  banner: document.querySelector('[data-freshness-banner]'),
  select: document.querySelector('[data-environment-select]'),
  classification: document.querySelector('[data-environment-classification]'),
  description: document.querySelector('[data-environment-description]'),
  environmentLabel: document.querySelector('[data-environment-label]'),
  mode: document.querySelector('[data-source-mode]'),
  generatedAt: document.querySelector('[data-generated-at]'),
  lastCollection: document.querySelector('[data-last-collection]'),
  eventCount: document.querySelector('[data-event-count]'),
  timeWindow: document.querySelector('[data-time-window]'),
  requestCount: document.querySelector('[data-request-count]'),
  failureCount: document.querySelector('[data-failure-count]'),
  expectedCount: document.querySelector('[data-expected-count]'),
  failureRate: document.querySelector('[data-failure-rate]'),
  averageDuration: document.querySelector('[data-average-duration]'),
  maximumDuration: document.querySelector('[data-maximum-duration]'),
  requestChart: document.querySelector('[data-request-chart]'),
  chartEmpty: document.querySelector('[data-chart-empty]'),
  operationsBody: document.querySelector('[data-operations-body]'),
  failuresBody: document.querySelector('[data-failures-body]'),
  alertList: document.querySelector('[data-alert-list]')
};

let environments = [];
let selectedEnvironmentId;
let refreshGeneration = 0;

async function initialize() {
  try {
    const registry = await getEnvironments();
    environments = registry.environments;
    selectedEnvironmentId = chooseInitialEnvironment(registry);
    populateEnvironmentSelector();
    elements.select.addEventListener('change', () => selectEnvironment(elements.select.value));
    elements.timeWindow.addEventListener('change', refreshStatus);
    await refreshStatus();
    setInterval(refreshStatus, REFRESH_INTERVAL_MS);
  } catch {
    renderUnavailable('ChatMonitor could not read its local environment registry.');
    elements.select.disabled = true;
  }
}

async function selectEnvironment(environmentId) {
  if (!environments.some(({ id }) => id === environmentId)) return;
  selectedEnvironmentId = environmentId;
  localStorage.setItem(selectionStorageKey, environmentId);
  const url = new URL(window.location.href);
  url.searchParams.set('environment', environmentId);
  window.history.replaceState({}, '', url);
  await refreshStatus();
}

async function refreshStatus() {
  const environmentId = selectedEnvironmentId;
  const generation = ++refreshGeneration;
  elements.select.disabled = true;
  try {
    const [status, dashboard] = await Promise.all([
      getEnvironmentStatus(environmentId),
      getEnvironmentDashboard(environmentId, Number(elements.timeWindow.value))
    ]);
    if (generation !== refreshGeneration || environmentId !== selectedEnvironmentId) return;
    const environment = environments.find(({ id }) => id === environmentId);
    renderFreshnessBanner(elements.banner, status);
    document.body.dataset.environmentClassification = environment.classification;
    elements.environmentLabel.textContent = status.environment.label;
    elements.classification.textContent = environment.classification;
    elements.classification.dataset.classification = environment.classification;
    elements.description.textContent = status.sourceMode === 'planned'
      ? `${status.environment.label} is registered, but collection will begin only after the environment is deployed and onboarded.`
      : `${status.environment.label} is collected independently; switching views does not stop other collectors.`;
    elements.mode.textContent = status.sourceMode;
    elements.generatedAt.textContent = new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'medium'
    }).format(new Date(status.generatedAt));
    elements.lastCollection.textContent = status.freshness.lastSuccessfulCollectionAt
      ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' })
        .format(new Date(status.freshness.lastSuccessfulCollectionAt))
      : 'Not yet collected';
    elements.eventCount.textContent = new Intl.NumberFormat().format(status.retention.eventCount);
    renderDashboard(dashboard);
  } catch {
    if (generation === refreshGeneration) {
      renderUnavailable(`ChatMonitor could not read status for ${environmentId}.`);
    }
  } finally {
    if (generation === refreshGeneration) elements.select.disabled = false;
  }
}

function renderDashboard({ overview, metrics, operations, failures, alerts }) {
  elements.requestCount.textContent = formatNumber(overview.requestCount);
  elements.failureCount.textContent = formatNumber(overview.failureCount);
  elements.expectedCount.textContent = formatNumber(overview.expected4xxCount);
  elements.failureRate.textContent = new Intl.NumberFormat(undefined, {
    style: 'percent', maximumFractionDigits: 1
  }).format(overview.failureRate);
  elements.averageDuration.textContent = formatDuration(overview.averageDurationMs);
  elements.maximumDuration.textContent = formatDuration(overview.maximumDurationMs);
  renderRequestChart(elements.requestChart, metrics.points);
  elements.chartEmpty.hidden = metrics.points.length > 0;
  renderOperations(operations.operations);
  renderFailures(failures.failures);
  renderAlerts(alerts.alerts);
}

function renderOperations(operations) {
  if (!operations.length) {
    elements.operationsBody.innerHTML = '<tr><td colspan="4">No operations in this window.</td></tr>';
    return;
  }
  elements.operationsBody.replaceChildren(...operations.map((operation) => row([
    `${operation.method} ${operation.route}`,
    formatNumber(operation.requestCount),
    formatNumber(operation.failureCount),
    formatDuration(operation.averageDurationMs)
  ])));
}

function renderFailures(failures) {
  if (!failures.length) {
    elements.failuresBody.innerHTML = '<tr><td colspan="3">No server failures in this window.</td></tr>';
    return;
  }
  elements.failuresBody.replaceChildren(...failures.map((failure) => row([
    new Intl.DateTimeFormat(undefined, { dateStyle: 'short', timeStyle: 'medium' })
      .format(new Date(failure.occurredAt)),
    `${failure.dimensions.method ?? 'unknown'} ${failure.dimensions.route ?? 'unknown'}`,
    failure.dimensions.errorCode ?? failure.dimensions.responseCode ?? '5xx'
  ])));
}

function renderAlerts(alerts) {
  elements.alertList.replaceChildren(...alerts.map((alert) => {
    const item = document.createElement('li');
    item.dataset.state = alert.state;
    const name = document.createElement('strong');
    name.textContent = `${alert.ruleId}: ${alert.state}`;
    const detail = document.createElement('span');
    detail.textContent = alert.detail;
    item.append(name, detail);
    return item;
  }));
}

function row(values) {
  const tableRow = document.createElement('tr');
  for (const value of values) {
    const cell = document.createElement('td');
    cell.textContent = value;
    tableRow.append(cell);
  }
  return tableRow;
}

function formatNumber(value) {
  return new Intl.NumberFormat().format(value);
}

function formatDuration(value) {
  return value === null || value === undefined ? 'No data' : `${Math.round(value)} ms`;
}

function chooseInitialEnvironment(registry) {
  const candidates = [
    new URL(window.location.href).searchParams.get('environment'),
    localStorage.getItem(selectionStorageKey),
    registry.defaultEnvironmentId
  ];
  return candidates.find((candidate) => environments.some(({ id }) => id === candidate))
    ?? environments[0].id;
}

function populateEnvironmentSelector() {
  elements.select.replaceChildren(...environments.map((environment) => {
    const option = document.createElement('option');
    option.value = environment.id;
    option.textContent = `${environment.label} · ${environment.classification}`;
    option.selected = environment.id === selectedEnvironmentId;
    return option;
  }));
  elements.select.disabled = false;
}

function renderUnavailable(message) {
  renderFreshnessBanner(elements.banner, { state: 'SOURCE_UNAVAILABLE', message });
  for (const element of [
    elements.environmentLabel,
    elements.mode,
    elements.generatedAt,
    elements.lastCollection,
    elements.eventCount
  ]) element.textContent = 'Unavailable';
  for (const element of [elements.requestCount, elements.failureCount, elements.expectedCount,
    elements.failureRate, elements.averageDuration, elements.maximumDuration]) {
    element.textContent = 'Unavailable';
  }
}

initialize();
