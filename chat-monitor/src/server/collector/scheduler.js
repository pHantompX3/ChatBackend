export function createScheduler({ collector, environmentId, intervalSeconds, logger, afterCycle }) {
  let timer = null;
  let activeCycle = null;
  let stopped = false;

  function run() {
    if (stopped || activeCycle) {
      return activeCycle;
    }
    activeCycle = collector.collect()
      .catch((error) => {
        logger.error('collector_scheduler_failure', {
          environmentId,
          errorName: error.name,
          errorMessage: error.message
        });
      })
      .finally(() => {
        try {
          afterCycle?.();
        } catch (error) {
          logger.error('collector_after_cycle_failure', {
            environmentId,
            errorName: error.name,
            errorMessage: error.message
          });
        }
      })
      .finally(() => {
        activeCycle = null;
      });
    return activeCycle;
  }

  return Object.freeze({
    async start() {
      stopped = false;
      await run();
      timer = setInterval(run, intervalSeconds * 1000);
      timer.unref();
    },
    async stop() {
      stopped = true;
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
      await activeCycle;
    },
    runNow: run
  });
}
