const REDACTED = '[REDACTED]';
const SENSITIVE_KEY = /(authorization|cookie|credential|password|secret|token)/i;

function sanitize(value) {
  if (Array.isArray(value)) {
    return value.map(sanitize);
  }

  if (!value || typeof value !== 'object') {
    return value;
  }

  return Object.fromEntries(
    Object.entries(value).map(([key, nested]) => [key, SENSITIVE_KEY.test(key) ? REDACTED : sanitize(nested)])
  );
}

export function createLogger(level = 'info', output = process.stdout) {
  const levels = ['debug', 'info', 'warn', 'error'];
  const threshold = level === 'silent' ? Number.POSITIVE_INFINITY : levels.indexOf(level);

  function write(eventLevel, message, details = {}) {
    if (levels.indexOf(eventLevel) < threshold) {
      return;
    }

    output.write(`${JSON.stringify({
      timestamp: new Date().toISOString(),
      level: eventLevel,
      message,
      ...sanitize(details)
    })}\n`);
  }

  return Object.freeze({
    debug: (message, details) => write('debug', message, details),
    info: (message, details) => write('info', message, details),
    warn: (message, details) => write('warn', message, details),
    error: (message, details) => write('error', message, details)
  });
}
