export class SourceUnavailableError extends Error {
  constructor(message, options) {
    super(message, options);
    this.name = 'SourceUnavailableError';
    this.code = 'SOURCE_UNAVAILABLE';
  }
}
