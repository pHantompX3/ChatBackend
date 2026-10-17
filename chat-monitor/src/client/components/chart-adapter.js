import Chart from 'chart.js/auto';

let activeChart;

export function renderRequestChart(canvas, points) {
  activeChart?.destroy();
  if (!points.length) {
    canvas.hidden = true;
    return;
  }
  canvas.hidden = false;
  activeChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels: points.map(({ bucketStart }) => new Intl.DateTimeFormat(undefined, {
        month: 'short', day: 'numeric', hour: 'numeric'
      }).format(new Date(bucketStart))),
      datasets: [
        {
          label: 'Requests',
          data: points.map(({ requestCount }) => requestCount),
          borderColor: getComputedStyle(document.body).getPropertyValue('--color-accent').trim(),
          backgroundColor: 'transparent',
          tension: 0.25
        },
        {
          label: 'Server failures',
          data: points.map(({ failureCount }) => failureCount),
          borderColor: '#ff7b72',
          backgroundColor: 'transparent',
          tension: 0.25
        }
      ]
    },
    options: {
      animation: false,
      responsive: true,
      maintainAspectRatio: false,
      scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
      plugins: { legend: { labels: { color: '#f0f6f4' } } }
    }
  });
}
