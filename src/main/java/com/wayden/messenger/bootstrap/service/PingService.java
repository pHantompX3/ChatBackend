package com.wayden.messenger.bootstrap.service;

import com.wayden.messenger.bootstrap.api.PingResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PingService {

  public PingResponse ping() {
    return new PingResponse("ok");
  }
}
