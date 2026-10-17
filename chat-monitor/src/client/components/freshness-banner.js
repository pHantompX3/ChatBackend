const LABELS = Object.freeze({
  CURRENT: 'Current',
  CATCHING_UP: 'Catching up',
  SOURCE_UNAVAILABLE: 'Source unavailable',
  COLLECTION_FAILED: 'Collection failed',
  HISTORY_GAP: 'History gap'
});

export function renderFreshnessBanner(element, status) {
  element.dataset.state = status.state;
  element.replaceChildren();

  const heading = document.createElement('strong');
  heading.textContent = LABELS[status.state] ?? 'Unknown state';

  const detail = document.createElement('span');
  detail.textContent = status.message;

  element.append(heading, detail);
}
