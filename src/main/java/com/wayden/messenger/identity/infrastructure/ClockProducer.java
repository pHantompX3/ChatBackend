package com.wayden.messenger.identity.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

@ApplicationScoped
public class ClockProducer {

  @Produces
  Clock systemClock() {
    return Clock.systemUTC();
  }
}
