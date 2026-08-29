package com.wayden.messenger.common.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpAuditQueueDispatcher {

  private static final Logger LOG = Logger.getLogger(HttpAuditQueueDispatcher.class);

  private final HttpAuditEventSink eventSink;
  private final HttpAuditDeadLetterHandler deadLetterHandler;
  private final ObjectMapper objectMapper;
  private final boolean asyncEnabled;
  private final int queueCapacity;
  private final boolean rabbitEnabled;
  private final String rabbitHost;
  private final Optional<String> rabbitHostCandidatesConfig;
  private final int rabbitPort;
  private final String rabbitUsername;
  private final Optional<String> rabbitPassword;
  private final String rabbitVhost;
  private final String rabbitExchange;
  private final String rabbitRoutingKey;
  private final String rabbitQueue;
  private final boolean rabbitProvisionTopology;
  private final boolean rabbitConsumerEnabled;
  private final int rabbitPrefetch;

  private final BooleanSupplier rabbitInitializer;

  private volatile boolean running;
  private volatile boolean rabbitActive;
  private BlockingQueue<HttpAuditEvent> localQueue;
  private Thread localWorkerThread;
  private Thread rabbitConsumerThread;
  private Thread rabbitRetryThread;
  private Connection rabbitConnection;
  private Channel rabbitPublishChannel;
  private Channel rabbitConsumeChannel;
  private String activeRabbitHost;

  @Inject
  public HttpAuditQueueDispatcher(
      HttpAuditEventSink eventSink,
      HttpAuditDeadLetterHandler deadLetterHandler,
      ObjectMapper objectMapper,
      @ConfigProperty(name = "chat.audit.async.enabled", defaultValue = "true")
          boolean asyncEnabled,
      @ConfigProperty(name = "chat.audit.async.queue-capacity", defaultValue = "1024")
          int queueCapacity,
      @ConfigProperty(name = "chat.audit.rabbitmq.enabled", defaultValue = "false")
          boolean rabbitEnabled,
      @ConfigProperty(name = "chat.audit.rabbitmq.host", defaultValue = "localhost")
          String rabbitHost,
      @ConfigProperty(name = "chat.audit.rabbitmq.host-candidates")
          Optional<String> rabbitHostCandidatesConfig,
      @ConfigProperty(name = "chat.audit.rabbitmq.port", defaultValue = "5672") int rabbitPort,
      @ConfigProperty(name = "chat.audit.rabbitmq.username", defaultValue = "wl_chat_queue")
          String rabbitUsername,
      @ConfigProperty(name = "chat.audit.rabbitmq.password") Optional<String> rabbitPassword,
      @ConfigProperty(name = "chat.audit.rabbitmq.vhost", defaultValue = "/") String rabbitVhost,
      @ConfigProperty(name = "chat.audit.rabbitmq.exchange", defaultValue = "audit.events")
          String rabbitExchange,
      @ConfigProperty(name = "chat.audit.rabbitmq.routing-key", defaultValue = "audit.completed")
          String rabbitRoutingKey,
      @ConfigProperty(name = "chat.audit.rabbitmq.queue", defaultValue = "audit.events")
          String rabbitQueue,
      @ConfigProperty(name = "chat.audit.rabbitmq.provision-topology", defaultValue = "true")
          boolean rabbitProvisionTopology,
      @ConfigProperty(name = "chat.audit.rabbitmq.consumer.enabled", defaultValue = "true")
          boolean rabbitConsumerEnabled,
      @ConfigProperty(name = "chat.audit.rabbitmq.consumer.prefetch", defaultValue = "25")
          int rabbitPrefetch) {
    this(
        eventSink,
        deadLetterHandler,
        objectMapper,
        asyncEnabled,
        queueCapacity,
        rabbitEnabled,
        rabbitHost,
        rabbitHostCandidatesConfig,
        rabbitPort,
        rabbitUsername,
        rabbitPassword,
        rabbitVhost,
        rabbitExchange,
        rabbitRoutingKey,
        rabbitQueue,
        rabbitProvisionTopology,
        rabbitConsumerEnabled,
        rabbitPrefetch,
        null);
  }

  private HttpAuditQueueDispatcher(
      HttpAuditEventSink eventSink,
      HttpAuditDeadLetterHandler deadLetterHandler,
      ObjectMapper objectMapper,
      boolean asyncEnabled,
      int queueCapacity,
      boolean rabbitEnabled,
      String rabbitHost,
      Optional<String> rabbitHostCandidatesConfig,
      int rabbitPort,
      String rabbitUsername,
      Optional<String> rabbitPassword,
      String rabbitVhost,
      String rabbitExchange,
      String rabbitRoutingKey,
      String rabbitQueue,
      boolean rabbitProvisionTopology,
      boolean rabbitConsumerEnabled,
      int rabbitPrefetch,
      BooleanSupplier rabbitInitializer) {
    this.eventSink = eventSink;
    this.deadLetterHandler = deadLetterHandler;
    this.objectMapper = objectMapper;
    this.asyncEnabled = asyncEnabled;
    this.queueCapacity = queueCapacity;
    this.rabbitEnabled = rabbitEnabled;
    this.rabbitHost = rabbitHost;
    this.rabbitHostCandidatesConfig = rabbitHostCandidatesConfig;
    this.rabbitPort = rabbitPort;
    this.rabbitUsername = rabbitUsername;
    this.rabbitPassword = rabbitPassword;
    this.rabbitVhost = rabbitVhost;
    this.rabbitExchange = rabbitExchange;
    this.rabbitRoutingKey = rabbitRoutingKey;
    this.rabbitQueue = rabbitQueue;
    this.rabbitProvisionTopology = rabbitProvisionTopology;
    this.rabbitConsumerEnabled = rabbitConsumerEnabled;
    this.rabbitPrefetch = rabbitPrefetch;
    this.rabbitInitializer = rabbitInitializer == null ? this::initializeRabbit : rabbitInitializer;
  }

  HttpAuditQueueDispatcher(
      HttpAuditEventSink eventSink,
      HttpAuditDeadLetterHandler deadLetterHandler,
      boolean asyncEnabled,
      int queueCapacity) {
    this(
        eventSink,
        deadLetterHandler,
        new ObjectMapper(),
        asyncEnabled,
        queueCapacity,
        false,
        "localhost",
        Optional.empty(),
        5672,
        "wl_chat_queue",
        Optional.empty(),
        "/",
        "audit.events",
        "audit.completed",
        "audit.events",
        true,
        false,
        25,
        null);
  }

  HttpAuditQueueDispatcher(
      HttpAuditEventSink eventSink,
      HttpAuditDeadLetterHandler deadLetterHandler,
      boolean asyncEnabled,
      int queueCapacity,
      BooleanSupplier rabbitInitializer) {
    this(
        eventSink,
        deadLetterHandler,
        new ObjectMapper(),
        asyncEnabled,
        queueCapacity,
        true,
        "localhost",
        Optional.empty(),
        5672,
        "wl_chat_queue",
        Optional.empty(),
        "/",
        "audit.events",
        "audit.completed",
        "audit.events",
        true,
        false,
        25,
        rabbitInitializer);
  }

  @PostConstruct
  void start() {
    running = true;
    if (rabbitEnabled) {
      if (rabbitInitializer.getAsBoolean()) {
        activateRabbitTransport();
      }
      startRabbitRetryLoop();
    }

    if (asyncEnabled) {
      startLocalWorker();
    }
  }

  private void activateRabbitTransport() {
    rabbitActive = true;
    if (rabbitConsumerEnabled
        && (rabbitConsumerThread == null || !rabbitConsumerThread.isAlive())) {
      startRabbitConsumer();
    }
    LOG.infof(
        "HTTP audit RabbitMQ transport enabled host=%s port=%d exchange=%s queue=%s",
        activeRabbitHost, rabbitPort, rabbitExchange, rabbitQueue);
  }

  public void submit(HttpAuditEvent event) {
    if (rabbitActive) {
      publishToRabbit(event);
      return;
    }

    submitToLocalFallback(event);
  }

  private void submitToLocalFallback(HttpAuditEvent event) {
    if (!asyncEnabled) {
      persistWithFailOpen(event);
      return;
    }

    if (localQueue == null || !localQueue.offer(event)) {
      handleDeadLetter(event, new IllegalStateException("Audit queue is full or unavailable"));
    }
  }

  AuditTransportStatus status() {
    int queuedEvents = localQueue == null ? 0 : localQueue.size();
    String mode = rabbitActive ? "rabbitmq" : asyncEnabled ? "local-async" : "local-sync";
    return new AuditTransportStatus(
        mode,
        rabbitEnabled && !rabbitActive,
        queuedEvents,
        activeRabbitHost == null ? "-" : activeRabbitHost);
  }

  @PreDestroy
  void shutdown() {
    running = false;
    if (localWorkerThread != null) {
      localWorkerThread.interrupt();
      try {
        localWorkerThread.join(500L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    if (rabbitConsumerThread != null) {
      rabbitConsumerThread.interrupt();
      try {
        rabbitConsumerThread.join(500L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    if (rabbitRetryThread != null) {
      rabbitRetryThread.interrupt();
      try {
        rabbitRetryThread.join(500L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    closeRabbitResources();
  }

  private void startLocalWorker() {
    localQueue = new LinkedBlockingQueue<>(queueCapacity);
    localWorkerThread = new Thread(this::runLocalWorker, "http-audit-dispatcher");
    localWorkerThread.setDaemon(true);
    localWorkerThread.start();
    LOG.infof("HTTP audit local async dispatcher started capacity=%d", queueCapacity);
  }

  private void runLocalWorker() {
    while (running || (localQueue != null && !localQueue.isEmpty())) {
      try {
        HttpAuditEvent event = localQueue.poll(200, TimeUnit.MILLISECONDS);
        if (event == null) {
          continue;
        }
        persistWithFailOpen(event);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void startRabbitRetryLoop() {
    if (rabbitRetryThread != null && rabbitRetryThread.isAlive()) {
      return;
    }
    rabbitRetryThread = new Thread(this::runRabbitRetryLoop, "http-audit-rabbit-retry");
    rabbitRetryThread.setDaemon(true);
    rabbitRetryThread.start();
  }

  private void runRabbitRetryLoop() {
    while (running && rabbitEnabled) {
      try {
        if (rabbitActive) {
          TimeUnit.SECONDS.sleep(2L);
          continue;
        }
        if (rabbitInitializer.getAsBoolean()) {
          activateRabbitTransport();
          continue;
        }
        TimeUnit.SECONDS.sleep(2L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception exception) {
        LOG.warn("Failed to initialize RabbitMQ audit transport; will retry", exception);
        try {
          TimeUnit.SECONDS.sleep(2L);
        } catch (InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  private boolean initializeRabbit() {
    String password = rabbitPassword.map(String::trim).orElse("");
    if (password.isBlank()) {
      LOG.warn(
          "RabbitMQ audit transport enabled but password is empty; falling back to local mode");
      return false;
    }

    List<String> hostCandidates = rabbitHostCandidates();
    Exception lastFailure = null;

    for (String candidateHost : hostCandidates) {
      try {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(candidateHost);
        factory.setPort(rabbitPort);
        factory.setUsername(rabbitUsername);
        factory.setPassword(password);
        factory.setVirtualHost(rabbitVhost);
        rabbitConnection = factory.newConnection("chat-backend-audit");
        rabbitPublishChannel = rabbitConnection.createChannel();
        rabbitConsumeChannel = rabbitConnection.createChannel();

        if (rabbitProvisionTopology) {
          rabbitPublishChannel.exchangeDeclare(rabbitExchange, "topic", true);
          rabbitPublishChannel.queueDeclare(rabbitQueue, true, false, false, null);
          rabbitPublishChannel.queueBind(rabbitQueue, rabbitExchange, rabbitRoutingKey);
        }
        rabbitConsumeChannel.basicQos(Math.max(1, rabbitPrefetch));
        activeRabbitHost = candidateHost;

        if (!candidateHost.equals(rabbitHost)) {
          LOG.infof(
              "RabbitMQ host fallback succeeded configuredHost=%s activeHost=%s",
              rabbitHost, candidateHost);
        }
        return true;
      } catch (Exception exception) {
        lastFailure = exception;
        closeRabbitResources();
      }
    }

    LOG.warnf(
        lastFailure,
        "Failed to initialize RabbitMQ audit transport; configuredHost=%s attemptedHosts=%s; falling back to local mode",
        rabbitHost,
        hostCandidates);
    return false;
  }

  private List<String> rabbitHostCandidates() {
    Set<String> hosts = new LinkedHashSet<>();

    rabbitHostCandidatesConfig
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .ifPresent(
            value -> {
              for (String token : value.split(",")) {
                String host = token.trim();
                if (!host.isBlank()) {
                  hosts.add(host);
                }
              }
            });

    if (hosts.isEmpty()) {
      String configuredHost = rabbitHost == null ? "" : rabbitHost.trim();
      if (!configuredHost.isBlank()) {
        hosts.add(configuredHost);
      }
    }

    return List.copyOf(hosts);
  }

  private void startRabbitConsumer() {
    rabbitConsumerThread = new Thread(this::runRabbitConsumer, "http-audit-rabbit-consumer");
    rabbitConsumerThread.setDaemon(true);
    rabbitConsumerThread.start();
  }

  private void runRabbitConsumer() {
    while (running && rabbitActive) {
      try {
        if (rabbitConsumeChannel == null || !rabbitConsumeChannel.isOpen()) {
          LOG.warn("RabbitMQ consumer channel is not open; stopping consumer loop");
          return;
        }

        GetResponse response = rabbitConsumeChannel.basicGet(rabbitQueue, false);
        if (response == null) {
          TimeUnit.MILLISECONDS.sleep(150);
          continue;
        }

        long deliveryTag = response.getEnvelope().getDeliveryTag();
        try {
          HttpAuditEvent event = objectMapper.readValue(response.getBody(), HttpAuditEvent.class);
          LOG.infof(
              "AUDIT QUEUE consume requestId=%s eventType=%s",
              event.requestId(), event.eventType());
          eventSink.persist(event);
          rabbitConsumeChannel.basicAck(deliveryTag, false);
          LOG.infof(
              "AUDIT DB persist requestId=%s eventType=%s", event.requestId(), event.eventType());
        } catch (Exception processingException) {
          safeNack(deliveryTag);
          handleDeadLetter(
              null,
              new IllegalStateException(
                  "Failed to process consumed audit event", processingException));
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception exception) {
        LOG.warn("RabbitMQ audit consumer loop failure", exception);
        deactivateRabbitAndRetry();
        return;
      }
    }
  }

  private void publishToRabbit(HttpAuditEvent event) {
    try {
      if (rabbitPublishChannel == null || !rabbitPublishChannel.isOpen()) {
        throw new IllegalStateException("RabbitMQ publish channel is not open");
      }
      byte[] payload = objectMapper.writeValueAsBytes(event);
      AMQP.BasicProperties properties =
          new AMQP.BasicProperties.Builder()
              .contentType("application/json")
              .deliveryMode(2)
              .build();
      synchronized (this) {
        rabbitPublishChannel.basicPublish(rabbitExchange, rabbitRoutingKey, properties, payload);
      }
      LOG.infof(
          "AUDIT QUEUE publish requestId=%s eventType=%s exchange=%s routingKey=%s",
          event.requestId(), event.eventType(), rabbitExchange, rabbitRoutingKey);
    } catch (Exception exception) {
      LOG.warn("Failed to publish audit event; switching to local fallback", exception);
      deactivateRabbitAndRetry();
      submitToLocalFallback(event);
    }
  }

  private synchronized void deactivateRabbitAndRetry() {
    closeRabbitResources();
    if (running) {
      startRabbitRetryLoop();
    }
  }

  private void safeNack(long deliveryTag) {
    try {
      if (rabbitConsumeChannel != null && rabbitConsumeChannel.isOpen()) {
        rabbitConsumeChannel.basicNack(deliveryTag, false, false);
      }
    } catch (IOException ioException) {
      LOG.warn("Failed to nack consumed audit message", ioException);
    }
  }

  private void closeRabbitResources() {
    rabbitActive = false;
    activeRabbitHost = null;
    closeQuietly(rabbitConsumeChannel);
    rabbitConsumeChannel = null;
    closeQuietly(rabbitPublishChannel);
    rabbitPublishChannel = null;
    if (rabbitConnection != null) {
      try {
        rabbitConnection.close();
      } catch (Exception ignored) {
        // no-op
      }
      rabbitConnection = null;
    }
  }

  private void closeQuietly(Channel channel) {
    if (channel == null) {
      return;
    }
    try {
      channel.close();
    } catch (Exception ignored) {
      // no-op
    }
  }

  private void persistWithFailOpen(HttpAuditEvent event) {
    try {
      eventSink.persist(event);
    } catch (RuntimeException exception) {
      handleDeadLetter(event, exception);
    }
  }

  private void handleDeadLetter(HttpAuditEvent event, Exception exception) {
    try {
      if (event != null) {
        deadLetterHandler.handle(event, exception);
      } else {
        LOG.warn("Audit dead-letter event unavailable for failed processing", exception);
      }
    } catch (RuntimeException deadLetterException) {
      LOG.error("HTTP audit dead-letter handler failed", deadLetterException);
    }
  }

  record AuditTransportStatus(
      String mode, boolean degraded, int localQueueDepth, String activeRabbitHost) {}
}
